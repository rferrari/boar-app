# Boar Designer (image lab)

Draws one illustration from a few words, on the phone, with no internet after a one-time model download. Status: **BETA**.

## The model

SDXS-512 tinySD distilled, Q8_0 (683 MB), run by stable-diffusion.cpp's `sd-cli` (MIT). Chosen because it is **small and fast**: it draws in a single step, so a picture takes 8 to 18 s on a phone CPU, and it fits next to the chat model.

| | |
|---|---|
| Downloaded from | huggingface.co/concedo/sdxs-512-tinySDdistilled-GGUF |
| SHA-256 | `409ab23582ee074c6b9d5395784fc0741b0599fb9d138686c69087c71678eb6a` |
| Based on | akleine/sdxs-512, from IDKiro/sdxs-512-dreamshaper |
| License | CreativeML Open RAIL++-M, with use restrictions; the download screen links it |
