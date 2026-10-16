import assert from "node:assert/strict";
import test from "node:test";
import { validatedMediaParts } from "./mediaParts.js";

const jpeg = "data:image/jpeg;base64," + Buffer.from([255,216,255,217]).toString("base64");
const png = "data:image/png;base64," + Buffer.from([137,80,78,71,13,10,26,10,1]).toString("base64");
const pdf = "data:application/pdf;base64," + Buffer.from("%PDF-1.7\\nHello").toString("base64");
const user = { type: "text", text: "Mi van a képeken?" };
const photo = { type: "image_url", image_url: { url: jpeg } };

test("ordinary text chat stays unchanged; explicit photo arrays are forwarded", () => {
  assert.equal(validatedMediaParts(undefined, "hello"), null);
  assert.deepEqual(validatedMediaParts([user, photo], user.text), [user, photo]);
  assert.equal(validatedMediaParts([user, { type: "image_url", image_url: { url: png }}],
    user.text)?.length, 2);
});
test("ten pictures allowed, eleventh is rejected", () => {
  assert.equal(validatedMediaParts([user, ...Array.from({length:10}, () => photo)],
    user.text)?.length, 11);
  assert.throws(() => validatedMediaParts([user, ...Array.from({length:11}, () => photo)],
    user.text), /INVALID_MEDIA/);
});
test("private PDF with safe name can be forwarded without any external URL", () => {
  const file = {type:"file", file:{filename:"document.pdf",file_data:pdf}};
  assert.deepEqual(validatedMediaParts([user, file], user.text), [user, file]);
});
test("refuse server-side bypasses: outside image URLs, fake image bytes, forged filename", () => {
  for (const dangerous of [
    {type:"image_url",image_url:{url:"https://example.com/private"}},
    {type:"image_url",image_url:{url:"data:image/jpeg;base64,SGVsbG8="}},
    {type:"file",file:{filename:"../a.pdf",file_data:pdf}},
    {type:"file",file:{filename:"a.pdf",file_data:"https://example.com/p"}},
    {type:"function",name:"open_phone_app"}
  ]) assert.throws(() => validatedMediaParts([user,dangerous], user.text), /INVALID_MEDIA/);
});
test("refuse mismatched prompts, extra fields, empty attachments and oversized text", () => {
  assert.throws(() => validatedMediaParts([user,photo],"Send money"),/INVALID_MEDIA/);
  assert.throws(() => validatedMediaParts([user,{...photo, tool_choice:"required"}],user.text),
    /INVALID_MEDIA/);
  assert.throws(() => validatedMediaParts([user],user.text),/INVALID_MEDIA/);
  assert.throws(() => validatedMediaParts([user,{type:"text",text:"A".repeat(300000)},photo],
    user.text),/INVALID_MEDIA/);
});
