# Zairenkai Local AI (Zairenkai-Intelligence-Engine)

The AI tab runs a small language model on the phone. It maps a question to typed calls from a
closed tool registry. The model's text never authorizes anything:

- read-only tools run;
- the one system tool (`fas.set_profile`) becomes a Core dry run that the user approves;
- Core applies the change and verifies it.

## Pieces and where they come from

| Piece | Source | In the APK |
|---|---|---|
| `zie-run`, the runner (`runtime/zie_run.cpp`, llama.cpp `b11500` core libraries only) | Intelligence-Engine `af31fd7`, built by its `scripts/build-android.sh` | `lib/arm64-v8a/libzierun.so`, `lib/x86_64/libzierun.so` |
| Tool registry (5 tools) | `registry/tools.json` | `assets/intelligence/tools.json` |
| Grammar the output is sampled under | `registry/output.gbnf` (generated from the registry) | `assets/intelligence/output.gbnf` |
| Model manifest (Qwen2.5-0.5B-Instruct q4_0: file name, size, SHA-256, source revision, licences, 1 GiB minimum memory) | `models/qwen2.5-0.5b-instruct-q4_0.json` | `assets/intelligence/model.json` |
| The model file (428,730,208 bytes) | Hugging Face `Qwen/Qwen2.5-0.5B-Instruct-GGUF` at the manifest's revision, republished unchanged as Intelligence-Engine's release `model-<model_id>` | **not packaged**: downloaded on request, or imported |
| Licences | Engine `LICENSE`/`NOTICE` (Apache-2.0), llama.cpp `LICENSE` (MIT) | `assets/licenses/zie-run/`, `assets/licenses/llama.cpp/` |

`integration/scripts/build-components.sh` builds and packages these at the commit
`integration/repository-lock.json` pins (`zie-run`). Two scripts check them:

- The engine's build script refuses a binary that imports a networking function, or that needs
  any library beyond libc, libm, libdl and liblog.
- `verify_integration.py` checks both ABIs, the assets and the licences, and refuses a packaged
  `.gguf` file.

armeabi-v7a gets no runner. There, `intelligence.analysis` reports
`NATIVE_COMPONENT_UNAVAILABLE`.

## The model file

The model is not in the APK. Packaging it would make the APK about 470 MB, and every app update
would download it again. The AI tab offers two ways to get it.

**Download (default).** "Download model (429 MB)" fetches
`https://github.com/ZairenkaiPrjctX/Zairenkai-Intelligence-Engine/releases/download/model-<model_id>/<file>`
(`ModelManifest.releaseUrl`).

- **The release.** The engine's Model release workflow drafts it from the manifest's pinned
  Hugging Face revision; the maintainer publishes it (Intelligence-Engine `docs/MODEL_RELEASE.md`).
- **When it runs.** It starts only when the user taps. On a metered network the app asks first.
- **Transport.** The same `HttpsTransport` and host allowlist as the updater (HTTPS only; GitHub
  and its release-asset hosts). No token is sent, and no device data.
- **Interruptions.** A pause or a dropped connection keeps the bytes, and the next tap resumes with
  a `Range` request. A server that ignores the range sends the whole file again.
- **Missing release.** A 404 is shown as "not published yet"; the import still works.

**Import.** The user picks the GGUF file the manifest names with the system file picker.

**Checks (both ways).** `ModelStore` writes the file into app-private storage (`files/models/`).
It hashes the file while writing and stops as soon as it grows past the expected size. It keeps
the file only if the size and SHA-256 match the manifest; otherwise it deletes it and says why.
A marker records the verified digest, so a 400 MB file is not re-hashed on every start. Deleting
the model removes the file and the marker.

## A turn

1. **Prompt.** `AiPrompt.build` builds the prompt byte for byte like the engine's
   `zie.agent.build_prompt` (`AiTest` compares them with the engine's own output). Chat-template
   tokens are removed from the user's text, which is cut to 500 code points.
2. **Model.** `ZieRuntime.generate` runs
   `libzierun.so -m <model> -g <grammar> -f <prompt file> -n 160 -t <≤4>`:
   - it runs as the app's uid, never through root;
   - the argv is fixed, and the prompt goes through a private file that is deleted afterwards;
   - the timeout is 180 s;
   - zie-run's exit statuses are reported as reasons, never replaced by an answer.
3. **Second check.** `AiPlanParser` re-checks the output against the registry's output schema:
   - exact keys and bounded strings;
   - an integer confidence;
   - at most 3 calls, each naming a registered tool with valid enum arguments.

   Anything else is shown as "No action taken", with the reason.
4. **Tools.** `LocalAgent` with `DeviceTools`:
   - `core.health`, `core.capabilities` and `fas.status` read through root, the way the rest of
     the app reads them;
   - `thermal.snapshot` runs zthermal as the app.

   The results are shown as labelled facts.
5. **Changes.** `fas.set_profile` becomes `CoreOperation.FasSetProfile` and goes through Core's
   `submit` with `dry_run`:
   - the approval card shows Core's verdict and rules;
   - Approve is enabled only when Core allows the change;
   - `LocalAgent.apply` submits for real and reports Core's outcome (applied or verified).

   With Core down, nothing can be applied. The AI never uses `zfasd ctl` directly.

## Availability (`intelligence.analysis`)

| State | When |
|---|---|
| `INITIALIZATION_FAILED` | The packaged registry or manifest does not load |
| `NATIVE_COMPONENT_UNAVAILABLE` | No `libzierun.so` for this ABI, or `zie-run --version` fails |
| `UNSUPPORTED_DEVICE` | Total memory is below the manifest's `min_memory_bytes` |
| `COMPONENT_NOT_INSTALLED` | The runner answers, but no verified model file is present |
| `NOT_VERIFIED` (usable) | The runner answers and the model matches its SHA-256; the assistant has not been validated on hardware |

## Evidence

- **JVM (Linux x86_64 host).** `AiTest` covers the registry, prompt parity, the parser on the
  real model's captured answers (`contracts/intelligence/`), runtime argv and exit codes, model
  import, the agent and the subsystem states.
- **Real runner through the agent.** `AiTest.theRealRunnerAnswersThroughTheAgent` ran the
  Kotlin agent with the host-built zie-run and the real model (`ZIE_RUN`, `ZIE_MODEL`). It
  answered "Switch FAS to the performance profile" with a `fas.set_profile` request in 8 s.
- **UI (Robolectric).** `AiUiTest`: a question becomes a dry run, Approve applies it through
  Core, read-only facts are shown, and a wrong model file is refused. The download starts only on
  request, waits for a yes on mobile data, and reports a release that is not published.
- **Download (JVM).** `AiTest`: resume after an interruption, restart when the server ignores
  the range, cancel, 404, and a tampered or oversized file deleted.
- **Screenshot gallery.** `preview-ai-chat-*` and `ai-model-*`.
- **APK.** Both `libzierun.so` binaries import no networking function and need only libc, libm
  and libdl (`llvm-nm`, `llvm-readelf`).

**Unverified**: nothing has run on an Android device. Inference speed, memory use and whether an
app-uid child process can run zie-run under SELinux are open questions. The 0.5B model is weak:
the engine's evaluation routed 9 of 16 prompts correctly. In the captured answers, it asked for
no tool on "Is Zairenkai Core healthy?". The app shows that as it is.
