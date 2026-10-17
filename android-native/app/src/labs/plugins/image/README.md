# Boar Designer (image lab)

Draws one illustration from a few words, on the phone, with no internet after a one-time model download. Status: **BETA**, on by default in the Labs build.

## The model: SDXS-512 tinySD distilled, Q8_0

| | |
|---|---|
| File | `sdxs-512-tinySDdistilled_Q8_0.gguf`, 683 MB (682,847,200 bytes) |
| SHA-256 | `409ab23582ee074c6b9d5395784fc0741b0599fb9d138686c69087c71678eb6a` |
| Downloaded from | huggingface.co/concedo/sdxs-512-tinySDdistilled-GGUF |
| Lineage | GGUF of akleine/sdxs-512, a fine-tune of IDKiro/sdxs-512-dreamshaper (teacher Dreamshaper 8 LCM, VAE TAESD) |
| License | CreativeML Open RAIL++-M on every step of that chain (checked 2026-10-17). It allows redistribution and requires its use restrictions (Attachment A) to reach users: the download screen names the main ones and links the license. |
| Engine | stable-diffusion.cpp's `sd-cli`, MIT (built for arm64, `jniLibs`) |

### Why this one

- **One step.** SDXS is distilled to draw in a single step (`--steps 1 --cfg-scale 1`). Ordinary Stable Diffusion needs 20 or more steps, which on a phone CPU means minutes per picture.
- **Small.** 683 MB fits next to the chat model on an 8 to 12 GB phone.
- **Runs on the CPU** through `sd-cli`, with no GPU or NPU code to maintain.
- **A license that allows shipping it** (OpenRAIL++, with use restrictions), unlike SD-Turbo and SDXL-Turbo (Stability's non-commercial license).

### Measured on the X6 (Oct 16, 4 big cores)

Square 384: 9 s. Portrait 320×448: 9 s, 384×512: 10 s, 448×640: 15 s. Square 640: 18 s. Banner 576×256: 8 s, 640×320: 11 s, 768×320: 13 s. Each size chip shows this phone's own average once it has drawn at that size.

## Known risks

- **No content filter.** There is no prompt blocklist and no image check; stable-diffusion.cpp has no safety checker, and a negative prompt does nothing at `--cfg-scale 1`. The model comes from Dreamshaper 8 and can draw explicit images when asked, crudely at 512 px and one step. Before Boar Designer leaves Labs it needs at least a prompt blocklist (EN and PT), and an image classifier after drawing.
- **Quality.** 512 px base, one step: fine for an illustration beside an answer, not for photos of real people or readable text.
- **Heat and battery.** Each drawing keeps 4 cores busy for 8 to 18 s.

## Other models (not planned)

`sd-cli` can run other Stable Diffusion models, but each needs its own steps, guidance, base size and license check, and most take minutes per picture on a phone. If it is ever offered: a short curated list, each gated by RAM, never any file from Hugging Face.
