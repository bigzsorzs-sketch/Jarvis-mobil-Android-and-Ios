import assert from "node:assert/strict";
import test from "node:test";
import { validatedTranscriptionAudio } from "./transcription.js";

function goodWav(): Buffer {
  const pcm = Buffer.alloc(3200);
  const h = Buffer.alloc(44);
  h.write("RIFF", 0, "ascii");
  h.writeUInt32LE(36 + pcm.length, 4);
  h.write("WAVEfmt ", 8, "ascii");
  h.writeUInt32LE(16, 16);
  h.writeUInt16LE(1, 20);
  h.writeUInt16LE(1, 22);
  h.writeUInt32LE(16000, 24);
  h.writeUInt32LE(32000, 28);
  h.writeUInt16LE(2, 32);
  h.writeUInt16LE(16, 34);
  h.write("data", 36, "ascii");
  h.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([h, pcm]);
}

test("accept bounded audio in each of the seven selectable languages", () => {
  for (const language of ["hu", "en", "de", "fr", "es", "it", "pl"]) {
    const wav = goodWav();
    const data = wav.toString("base64");
    assert.deepEqual(validatedTranscriptionAudio({
      model: "ignored-by-server",
      input_audio: { format: "wav", data }, language
    }), { data, format: "wav", language });
  }
});

test("reject wrong audio content, unexpected format, incorrect lengths and languages", () => {
  const valid = goodWav();
  const base = { input_audio: { format: "wav", data: valid.toString("base64") }, language: "hu" };
  assert.equal(validatedTranscriptionAudio(undefined), null);
  assert.equal(validatedTranscriptionAudio({ ...base, language: "unknown" }), null);
  assert.equal(validatedTranscriptionAudio({ ...base, input_audio: { ...base.input_audio, format: "mp3" } }), null);
  assert.equal(validatedTranscriptionAudio({ ...base, input_audio: { ...base.input_audio, data: "AAAA" } }), null);
  const stereo = Buffer.from(valid);
  stereo.writeUInt16LE(2, 22);
  assert.equal(validatedTranscriptionAudio({
    ...base, input_audio: { format: "wav", data: stereo.toString("base64") }
  }), null);
  const fake = Buffer.from(valid);
  fake.writeUInt32LE(44100, 24);
  assert.equal(validatedTranscriptionAudio({
    ...base, input_audio: { format: "wav", data: fake.toString("base64") }
  }), null);
  const wrongSize = Buffer.from(valid);
  wrongSize.writeUInt32LE(1, 40);
  assert.equal(validatedTranscriptionAudio({
    ...base, input_audio: { format: "wav", data: wrongSize.toString("base64") }
  }), null);
});
