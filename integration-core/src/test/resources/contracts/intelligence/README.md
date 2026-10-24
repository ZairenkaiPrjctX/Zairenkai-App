# Zairenkai Local AI (Zairenkai-Intelligence-Engine)

- `tools.json`, `output.gbnf` and `model.json` are byte copies of `registry/tools.json`,
  `registry/output.gbnf` and
  `models/qwen2.5-0.5b-instruct-q4_0.json` at Zairenkai-Intelligence-Engine `af31fd7`. They are
  the files `build-components.sh` packages as `assets/intelligence/`.
- `prompt-<case>.txt` is what the engine's own `zie.agent.build_prompt` returned for the
  utterance `cases.json` names, at `af31fd7` (Python 3.13). `AiTest` checks that the app's
  `AiPrompt.build` produces the same bytes.
- `output-<case>.json` is the stdout of the real runner on that prompt, captured on a Linux
  x86_64 host on 2026-10-23:

  ```
  zie-run -m qwen2.5-0.5b-instruct-q4_0.gguf -g registry/output.gbnf -f prompt-<case>.txt -n 160 -t 4
  ```

  `zie-run` built from `runtime/zie_run.cpp` at `af31fd7` against llama.cpp `b11500`; the model file
  matched the manifest's SHA-256. Exit status 0 for every case. The answers are the model's, mistakes
  included: it asks for no tool on "Is Zairenkai Core healthy?", and the injection attempt yields a
  `fas.set_profile` request, which the app turns into a Core dry run that the user still has to
  approve.
