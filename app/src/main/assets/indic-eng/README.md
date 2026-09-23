---
license: mit
---

# Usage
We recommend using inference engine from [IndicTrans2](https://github.com/AI4Bharat/IndicTrans2?tab=readme-ov-file#ct2-inference) for inference of these models. Please execute the following block of code from the root directory:

```python3
from inference.engine import Model

model = Model(ckpt_dir, model_type="ctranslate2")

sents = [sent1, sent2,...]

# for a batch of sentences
model.batch_translate(sents, src_lang, tgt_lang)

# for a paragraph
model.translate_paragraph(text, src_lang, tgt_lang)
```

# Citation

If you use these models, please cite the following work:

```bibtex
@inproceedings{gumma-etal-2025-towards,
    title = "Towards Inducing Long-Context Abilities in Multilingual Neural Machine Translation Models",
    author = "Gumma, Varun  and
      Chitale, Pranjal A  and
      Bali, Kalika",
    editor = "Chiruzzo, Luis  and
      Ritter, Alan  and
      Wang, Lu",
    booktitle = "Proceedings of the 2025 Conference of the Nations of the Americas Chapter of the Association for Computational Linguistics: Human Language Technologies (Volume 1: Long Papers)",
    month = apr,
    year = "2025",
    address = "Albuquerque, New Mexico",
    publisher = "Association for Computational Linguistics",
    url = "https://aclanthology.org/2025.naacl-long.366/",
    pages = "7158--7170",
    ISBN = "979-8-89176-189-6"
}
```

