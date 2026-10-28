import { Mic, MicOff, Send, Volume2, VolumeX } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router";
import { api, errorMessage } from "~/lib/api";
import { formatPrice } from "~/lib/format";
import type { AssistantResponse } from "~/lib/types";
import { ProductImage } from "./ProductCard";

// The Web Speech API isn't in TypeScript's DOM types yet; this is the small part we use.
interface SpeechRecognitionLike {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  onresult: ((e: { results: ArrayLike<ArrayLike<{ transcript: string }> & { isFinal: boolean }> }) => void) | null;
  onerror: ((e: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
}
type SpeechRecognitionCtor = new () => SpeechRecognitionLike;

function speechRecognition(): SpeechRecognitionCtor | null {
  const w = window as unknown as { SpeechRecognition?: SpeechRecognitionCtor; webkitSpeechRecognition?: SpeechRecognitionCtor };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

type Status = "idle" | "listening" | "thinking";

const SUGGESTIONS = ["Gift for my dad under ₹5000", "Shoes for running", "Headphones for music"];

const STATUS_TEXT: Record<Status, string> = {
  idle: "Tap the orb and tell me what you're looking for",
  listening: "Listening…",
  thinking: "Finding the best picks…",
};

/** Siri-style shopping assistant: speak or type a request and get product picks from the backend. */
export function VoiceAssistant() {
  const [status, setStatus] = useState<Status>("idle");
  const [input, setInput] = useState("");
  const [transcript, setTranscript] = useState("");
  const [answer, setAnswer] = useState<AssistantResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [canListen, setCanListen] = useState(false);
  const [muted, setMuted] = useState(false);
  const recognition = useRef<SpeechRecognitionLike | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setCanListen(speechRecognition() !== null);
    return () => {
      recognition.current?.stop();
      window.speechSynthesis?.cancel();
    };
  }, []);

  function speak(text: string) {
    if (muted || !("speechSynthesis" in window)) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "en-IN";
    window.speechSynthesis.speak(utterance);
  }

  async function ask(message: string) {
    const text = message.trim();
    if (!text || status === "thinking") return;
    setTranscript(text);
    setInput("");
    setError(null);
    setStatus("thinking");
    try {
      const res = await api<AssistantResponse>("/assistant", { method: "POST", body: { message: text.slice(0, 300) } });
      setAnswer(res);
      speak(res.reply);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setStatus("idle");
    }
  }

  function toggleListening() {
    if (status === "listening") {
      recognition.current?.stop();
      return;
    }
    const Ctor = speechRecognition();
    if (!Ctor) {
      inputRef.current?.focus();
      return;
    }
    window.speechSynthesis?.cancel();
    const rec = new Ctor();
    rec.lang = "en-IN";
    rec.interimResults = true;
    rec.continuous = false;
    let finalText = "";
    rec.onresult = (e) => {
      const text = Array.from(e.results).map((r) => r[0].transcript).join("");
      setTranscript(text);
      if (e.results[e.results.length - 1].isFinal) finalText = text;
    };
    rec.onerror = (e) => {
      if (e.error === "not-allowed") setError("Microphone access was blocked. You can type instead.");
      else if (e.error !== "no-speech" && e.error !== "aborted") setError("I couldn't hear that. Try again or type.");
    };
    rec.onend = () => {
      recognition.current = null;
      setStatus("idle");
      if (finalText) void ask(finalText);
    };
    recognition.current = rec;
    setError(null);
    setTranscript("");
    setStatus("listening");
    rec.start();
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void ask(input);
  }

  const active = status !== "idle";

  return (
    <div className="relative flex flex-col rounded-3xl bg-white/10 p-5 ring-1 ring-white/20 backdrop-blur-md sm:p-6">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-sm font-semibold">Curato Assistant</div>
          <div className="text-xs text-indigo-100">Ask by voice or text</div>
        </div>
        <button
          type="button"
          onClick={() => {
            setMuted((m) => !m);
            window.speechSynthesis?.cancel();
          }}
          className="rounded-full p-2 text-indigo-100 hover:bg-white/10 hover:text-white"
          aria-label={muted ? "Turn voice replies on" : "Turn voice replies off"}
          title={muted ? "Voice replies off" : "Voice replies on"}
        >
          {muted ? <VolumeX className="size-4" /> : <Volume2 className="size-4" />}
        </button>
      </div>

      <div className="mt-4 flex flex-col items-center text-center">
        <button
          type="button"
          onClick={toggleListening}
          disabled={status === "thinking"}
          className="group relative grid size-32 place-items-center rounded-full outline-none focus-visible:ring-4 focus-visible:ring-white/60 sm:size-36"
          aria-label={status === "listening" ? "Stop listening" : "Talk to the assistant"}
        >
          {active && (
            <>
              <span className="absolute inset-3 rounded-full bg-fuchsia-300/40 motion-safe:animate-orb-ripple" />
              <span className="absolute inset-3 rounded-full bg-sky-300/40 [animation-delay:0.8s] motion-safe:animate-orb-ripple" />
            </>
          )}
          <span
            className={`absolute inset-3 overflow-hidden rounded-full shadow-2xl shadow-fuchsia-900/40 ${
              active ? "motion-safe:animate-orb-breathe" : ""
            }`}
          >
            <span className="absolute -inset-6 bg-[conic-gradient(from_0deg,#f472b6,#a78bfa,#38bdf8,#34d399,#f472b6)] blur-md motion-safe:animate-orb-spin" />
            <span className="absolute -inset-2 bg-[conic-gradient(from_180deg,transparent,#818cf8_25%,transparent_50%,#f0abfc_75%,transparent)] opacity-80 mix-blend-screen blur-sm motion-safe:animate-orb-spin-reverse" />
            <span className="absolute inset-[18%] rounded-full bg-white/30 blur-xl" />
            <span className="absolute left-[24%] top-[16%] h-[22%] w-[34%] rounded-full bg-white/60 blur-md" />
          </span>
          <span className="relative grid size-11 place-items-center rounded-full bg-white/20 ring-1 ring-white/40 backdrop-blur transition group-hover:scale-110">
            {status === "listening" ? <MicOff className="size-5" /> : <Mic className="size-5" />}
          </span>
        </button>

        <p className="mt-3 min-h-5 text-sm text-indigo-50" aria-live="polite">
          {STATUS_TEXT[status]}
        </p>
        {transcript && <p className="mt-1 line-clamp-2 text-base font-medium">“{transcript}”</p>}
      </div>

      {error && <p className="mt-3 rounded-xl bg-rose-500/20 px-3 py-2 text-sm text-rose-50 ring-1 ring-rose-200/30">{error}</p>}

      {answer && !error && (
        <div className="mt-4 space-y-2" aria-live="polite">
          <p className="rounded-2xl rounded-tl-sm bg-white/15 px-3.5 py-2.5 text-sm">{answer.reply}</p>
          <ul className="max-h-56 space-y-1.5 overflow-y-auto pr-1">
            {answer.items.map(({ product, reason }) => (
              <li key={product.id}>
                <Link
                  to={`/products/${product.id}`}
                  className="flex items-center gap-3 rounded-xl bg-white p-2 text-slate-900 transition hover:bg-indigo-50"
                >
                  <ProductImage product={product} className="size-11 shrink-0 rounded-lg bg-slate-50 p-1" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold">{product.name}</span>
                    <span className="block truncate text-xs text-slate-500">{reason}</span>
                  </span>
                  <span className="shrink-0 text-sm font-semibold text-indigo-700">{formatPrice(product.price)}</span>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      )}

      {!answer && !error && (
        <div className="mt-4 flex flex-wrap justify-center gap-2">
          {SUGGESTIONS.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => void ask(s)}
              disabled={status === "thinking"}
              className="rounded-full bg-white/10 px-3 py-1.5 text-xs font-medium ring-1 ring-white/25 hover:bg-white/20"
            >
              {s}
            </button>
          ))}
        </div>
      )}

      <form onSubmit={onSubmit} className="mt-4 flex items-center gap-2 rounded-full bg-white/15 p-1.5 ring-1 ring-white/25 focus-within:ring-white/60">
        <input
          ref={inputRef}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          maxLength={300}
          placeholder={canListen ? "Or type, e.g. a watch for my wife" : "Type, e.g. a watch for my wife"}
          className="min-w-0 flex-1 bg-transparent px-3 text-sm text-white placeholder:text-indigo-100/80 outline-none"
          aria-label="Ask the shopping assistant"
        />
        <button
          type="submit"
          disabled={!input.trim() || status === "thinking"}
          className="grid size-9 shrink-0 place-items-center rounded-full bg-white text-indigo-700 transition hover:bg-indigo-50 disabled:opacity-50"
          aria-label="Send"
        >
          <Send className="size-4" />
        </button>
      </form>
    </div>
  );
}
