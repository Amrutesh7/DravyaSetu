# Knowledge Service

## Use Gemini for RAG answers

The answer generator uses the Gemini API. To restore it after the Groq change:

1. Create or copy a Gemini API key from [Google AI Studio](https://aistudio.google.com/app/apikey).
2. Open Command Prompt in the `knowledge-service` folder and install its dependencies:

   ```cmd
   cd C:\Users\karim\DravyaSetu\DravyaSetu\knowledge-service
   pip install -r requirements.txt
   ```

3. Set the key in that same Command Prompt window (replace the placeholder):

   ```cmd
   set GEMINI_API_KEY=paste-your-key-here
   ```

4. Run a quick answer check:

   ```cmd
   python -m app.rag.generator "What is Pudina used for?" --plant PLANT_023
   ```

5. Start or restart the knowledge-service API from that same window so it
   inherits `GEMINI_API_KEY`. If the API is already running in another terminal,
   stop it and start it again after setting the variable.

The `set` command only lasts for the current Command Prompt window. To store the
key for new windows too, run `setx GEMINI_API_KEY "paste-your-key-here"`, then
open a new Command Prompt. Do not include angle brackets or commit your real key.

The generator also accepts `GOOGLE_API_KEY` as an alternate variable name.
`LLM_MODELS` can optionally override the comma-separated Gemini model fallback
chain. Do not commit your real key; `.env.example` contains only a placeholder.
