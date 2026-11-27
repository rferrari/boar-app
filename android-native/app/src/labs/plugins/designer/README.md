# Boar Designer (image lab)

Draws one illustration from a few words, on the phone, with no internet after a one-time model download. Status: **ALPHA**, off until you turn it on in Boar Labs.

## The model

SDXS-512 tinySD distilled, Q8_0 (683 MB), run by stable-diffusion.cpp's `sd-cli` (MIT). Chosen because it is **small and fast**: it draws in a single step on the phone's CPU, and it fits next to the chat model.

| | |
|---|---|
| Downloaded from | huggingface.co/concedo/sdxs-512-tinySDdistilled-GGUF |
| SHA-256 | `409ab23582ee074c6b9d5395784fc0741b0599fb9d138686c69087c71678eb6a` |
| Based on | akleine/sdxs-512, from IDKiro/sdxs-512-dreamshaper |
| License | CreativeML Open RAIL++-M, with use restrictions; the download screen links it |

## Size and crop

Designer draws at 512x512 only, the size SDXS-512 was trained at (BOAR-88, 26 Nov 2026). Larger or non-square sizes
broke the pictures on the F3 with the same seeds: two mouths and a doubled nose at 640x640, a three-eared mouse, a
distorted face at 448x640. A 512 drawing takes about 11 s there.

Other shapes come from **Crop**, the second tool on the Designer page: 16:9, 4:3, 9:16 or free. It saves the frame as a
copy next to the drawing (`<time>-<style>-crop-<w>x<h>.png`, with the drawing's recipe), so the copy is in the gallery
and can go to Press; the 512 original stays. The chat's 🎨 Illustrate drawer has the style only: a chat drawing is
cropped by opening it in Designer.

Older drawings made at other sizes keep their size and still open; a saved default or recipe with another size is read
as 512.
