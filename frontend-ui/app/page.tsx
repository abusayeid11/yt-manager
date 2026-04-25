"use client";

import { useState, FormEvent } from "react";

export default function Home() {
  const [url, setUrl] = useState("");
  const [transcript, setTranscript] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!url.trim()) return;

    setLoading(true);
    setError("");
    setTranscript("");

    try {
      const response = await fetch("/api/v1/transcript", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url }),
      });

      if (!response.ok) {
        throw new Error("Failed to fetch transcript");
      }

      const data = await response.json();
      setTranscript(data.rawText);
    } catch (err) {
      setError("Could not fetch transcript. Make sure the backend is running.");
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = () => {
    navigator.clipboard.writeText(transcript);
  };

  return (
    <main className="min-h-screen bg-zinc-50 flex flex-col items-center justify-center p-8">
      <div className="w-full max-w-2xl">
        <h1 className="text-3xl font-bold text-center mb-8 text-zinc-800">
          YouTube Transcript Fetcher
        </h1>

        <form onSubmit={handleSubmit} className="flex gap-3 mb-8">
          <input
            type="text"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="Enter YouTube URL or Video ID"
            className="flex-1 px-4 py-3 rounded-lg border border-zinc-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button
            type="submit"
            disabled={loading || !url.trim()}
            className="px-6 py-3 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? "Loading..." : "Fetch Transcript"}
          </button>
        </form>

        {error && (
          <p className="text-red-600 text-center mb-4">{error}</p>
        )}

        {transcript && (
          <div className="bg-white rounded-lg shadow-md p-4">
            <div className="flex justify-between items-center mb-3">
              <h2 className="font-semibold text-zinc-700">Transcript</h2>
              <button
                onClick={copyToClipboard}
                className="px-3 py-1 text-sm bg-zinc-100 text-zinc-700 rounded hover:bg-zinc-200"
              >
                Copy to Clipboard
              </button>
            </div>
            <div className="max-h-96 overflow-y-auto p-3 bg-zinc-50 rounded border border-zinc-200">
              <p className="whitespace-pre-wrap text-zinc-700">{transcript}</p>
            </div>
          </div>
        )}
      </div>
    </main>
  );
}