// Laya ONNX latency and memory on an Android phone, without the app.
//
//   phone_bench <model.onnx> [threads=4] [runs=10]
//
// Loads the model with ONNX Runtime's CPU provider and times one yes/no question (two answer
// markers) at 64, 256 and 512 tokens: the timing doesn't depend on which tokens they are, so
// the inputs are synthetic. Prints load time, the median ms per pass and peak RSS.
// Build and run: scripts/laya/phone_bench.sh (docs/LAYA.md).
#include <onnxruntime_cxx_api.h>

#include <sys/resource.h>

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <string>
#include <vector>

static double now_ms() {
  return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

static long peak_rss_mb() {
  rusage u{};
  getrusage(RUSAGE_SELF, &u);
  return u.ru_maxrss / 1024;  // kilobytes on Linux
}

int main(int argc, char** argv) {
  if (argc < 2) {
    std::fprintf(stderr, "usage: %s <model.onnx> [threads] [runs]\n", argv[0]);
    return 2;
  }
  const int threads = argc > 2 ? std::atoi(argv[2]) : 4;
  const int runs = argc > 3 ? std::atoi(argv[3]) : 10;

  Ort::Env env(ORT_LOGGING_LEVEL_WARNING, "laya");
  Ort::SessionOptions so;
  so.SetIntraOpNumThreads(threads);
  so.SetInterOpNumThreads(1);
  so.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

  const double t0 = now_ms();
  Ort::Session session(env, argv[1], so);
  std::printf("load %.0f ms, rss after load %ld MB, threads %d\n", now_ms() - t0, peak_rss_mb(), threads);

  Ort::MemoryInfo mem = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
  const char* in_names[] = {"input_ids", "attention_mask", "marker_pos", "marker_mask", "qtype"};
  const char* out_names[] = {"logits", "act_logits"};

  for (int64_t len : {64, 256, 512}) {
    std::vector<int64_t> ids(len), attn(len, 1), marker_pos = {4, 6}, qtype = {2};
    for (int64_t i = 0; i < len; i++) ids[i] = 1000 + (i * 7919) % 50000;
    bool marker_mask[2] = {true, true};
    const int64_t seq_shape[] = {1, len}, mk_shape[] = {1, 2}, q_shape[] = {1};

    std::vector<Ort::Value> inputs;
    inputs.push_back(Ort::Value::CreateTensor<int64_t>(mem, ids.data(), ids.size(), seq_shape, 2));
    inputs.push_back(Ort::Value::CreateTensor<int64_t>(mem, attn.data(), attn.size(), seq_shape, 2));
    inputs.push_back(Ort::Value::CreateTensor<int64_t>(mem, marker_pos.data(), 2, mk_shape, 2));
    inputs.push_back(Ort::Value::CreateTensor<bool>(mem, marker_mask, 2, mk_shape, 2));
    inputs.push_back(Ort::Value::CreateTensor<int64_t>(mem, qtype.data(), 1, q_shape, 1));

    session.Run(Ort::RunOptions{nullptr}, in_names, inputs.data(), 5, out_names, 2);  // warm-up
    std::vector<double> ms;
    for (int r = 0; r < runs; r++) {
      const double t = now_ms();
      session.Run(Ort::RunOptions{nullptr}, in_names, inputs.data(), 5, out_names, 2);
      ms.push_back(now_ms() - t);
    }
    std::sort(ms.begin(), ms.end());
    std::printf("%4lld tokens: median %.0f ms, fastest %.0f ms, peak rss %ld MB\n", (long long) len, ms[ms.size() / 2], ms.front(),
                peak_rss_mb());
  }
  return 0;
}
