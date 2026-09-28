#!/usr/bin/env python3
"""Export a Laya checkpoint to ONNX (fp32 and int8) and check it against PyTorch.

    python scripts/laya/export_onnx.py [--model convaiinnovations/laya-multilingual] [--out build/laya]

Writes <out>/laya-fp32.onnx, <out>/laya-int8.onnx and <out>/tokenizer/, then prints, per variant,
file size, the largest probability difference from PyTorch on a fixed question set, and CPU
latency per forward pass. The ONNX inputs and outputs are the ones laya's own ONNXAgent feeds
(laya/onnx_agent.py): input_ids, attention_mask, marker_pos, marker_mask, qtype -> logits,
act_logits. Needs torch, transformers, laya, onnx, onnxruntime (see docs/LAYA.md).
"""
import argparse
import os
import time

import numpy as np
import torch
from laya.agent import Agent
from laya.common import QTYPES, build_sequence, collate_items, render_options

# One passage-relevance and one urgency question per text: the two uses planned for BOAR.
TEXTS = [
    "Canberra is the capital city of Australia, located at the northern end of the Australian Capital Territory.",
    "Photosynthesis is the process by which plants use sunlight, water and carbon dioxide to make glucose.",
    "Help, my friend collapsed on the trail and is not breathing, what do I do?",
    "What time does the museum open on Sundays?",
]
QUESTIONS = [
    {"type": "noul", "instructions": "Does this text help answer the question: What is the capital of Australia?"},
    {"type": "noul", "instructions": "Is the writer describing an emergency that needs urgent help?"},
]


def items_for(agent, text):
    cfg = agent.cfg
    out = []
    for q in QUESTIONS:
        iq = agent._to_internal(q)
        ids, markers = build_sequence(agent.tok, text, iq, cfg.get("max_len", 512), cfg.get("head_max_len", 192))
        assert len(markers) == len(render_options(iq))
        out.append({"ids": ids, "markers": markers, "qtype": QTYPES[iq["t"]]})
    return out


def feeds(batch):
    return {
        "input_ids": batch["input_ids"].numpy().astype(np.int64),
        "attention_mask": batch["attention_mask"].numpy().astype(np.int64),
        "marker_pos": batch["marker_pos"].numpy().astype(np.int64),
        "marker_mask": batch["marker_mask"].numpy().astype(bool),
        "qtype": batch["qtype"].numpy().astype(np.int64),
    }


def yes_probs(logits, n_markers):
    z = logits[:, :n_markers]
    p = np.exp(z - z.max(-1, keepdims=True))
    return (p / p.sum(-1, keepdims=True))[:, 1]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="convaiinnovations/laya-multilingual")
    ap.add_argument("--out", default="build/laya")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    agent = Agent(args.model, device="cpu")
    model = agent.model.float().eval()
    agent.tok.save_pretrained(os.path.join(args.out, "tokenizer"))
    n_params = sum(p.numel() for p in model.parameters())
    emb = model.encoder.get_input_embeddings().weight.numel()
    print(f"{args.model}: {n_params / 1e6:.0f}M parameters, of which {emb / 1e6:.0f}M are the token embedding table")

    batches = [collate_items([items_for(agent, t)], agent.tok.pad_token_id) for t in TEXTS]
    with torch.no_grad():
        ref = [model(**{k: b[k] for k in ("input_ids", "attention_mask", "marker_pos", "marker_mask", "qtype")})[0].numpy() for b in batches]

    fp32 = os.path.join(args.out, "laya-fp32.onnx")
    batch, tokens, options = torch.export.Dim("batch", max=16), torch.export.Dim("tokens", min=8, max=1024), torch.export.Dim("options", min=1, max=32)
    b0 = batches[0]
    torch.onnx.export(
        model,
        (b0["input_ids"], b0["attention_mask"], b0["marker_pos"], b0["marker_mask"], b0["qtype"]),
        fp32,
        input_names=["input_ids", "attention_mask", "marker_pos", "marker_mask", "qtype"],
        output_names=["logits", "act_logits"],
        # The dynamo exporter keeps the sequence length symbolic. The TorchScript one baked it into
        # the head's attention reshapes, so any other length failed at run time.
        dynamic_shapes={
            "input_ids": {0: batch, 1: tokens},
            "attention_mask": {0: batch, 1: tokens},
            "marker_pos": {0: batch, 1: options},
            "marker_mask": {0: batch, 1: options},
            "qtype": {0: batch},
        },
        opset_version=18,
        dynamo=True,
        external_data=False,
    )

    import onnxruntime as ort
    from onnxruntime.quantization import QuantType, quantize_dynamic

    # The exporter leaves intermediate shape annotations that ONNX's shape inference (run inside
    # quantize_dynamic) rejects ("inferred shape and existing shape differ"). Drop them and let
    # quantization infer them again.
    import onnx

    m = onnx.load(fp32)
    del m.graph.value_info[:]
    clean = os.path.join(args.out, "laya-fp32-clean.onnx")
    onnx.save(m, clean)
    int8 = os.path.join(args.out, "laya-int8.onnx")
    quantize_dynamic(clean, int8, weight_type=QuantType.QInt8)
    os.remove(clean)

    torch_yes = [yes_probs(r, 2) for r in ref]
    for label, path in (("fp32", fp32), ("int8", int8)):
        so = ort.SessionOptions()
        so.intra_op_num_threads = 4
        sess = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
        diffs, times = [], []
        for b, want in zip(batches, torch_yes):
            f = feeds(b)
            sess.run(["logits"], f)  # warm-up
            t = time.perf_counter()
            got = yes_probs(sess.run(["logits"], f)[0], 2)
            times.append((time.perf_counter() - t) * 1000 / f["input_ids"].shape[0])
            diffs.append(float(np.abs(got - want).max()))
        size = sum(os.path.getsize(os.path.join(args.out, n)) for n in os.listdir(args.out) if n.startswith(os.path.basename(path)))
        print(f"{label}: {size / 1e6:.0f} MB, max P(yes) difference from PyTorch {max(diffs):.4f}, "
              f"{np.median(times):.1f} ms per question (desktop CPU, 4 threads)")

    print("\nP(yes) per text, PyTorch:")
    for t, p in zip(TEXTS, torch_yes):
        print(f"  relevant to 'capital of Australia' {p[0]:.2f} | emergency {p[1]:.2f} | {t[:60]}")


if __name__ == "__main__":
    main()
