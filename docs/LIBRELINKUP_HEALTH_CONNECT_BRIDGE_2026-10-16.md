# Jarvis Android — LibreLink glucose through a Health Connect bridge (2026-10-16)

## Confirmed capability, not a direct Abbott integration

Jarvis **already reads** user-authorised Android Health Connect `BloodGlucoseRecord` data on Android 14+ (`JarvisHealthGlucose.latest`), shows the newest authorised record with its timestamp, and can answer a voice query without using an LLM to invent measurements (`JarvisGlucoseVoice.reply`). It **does not** query LibreLink's private local database, read the Libre 2 NFC radio stream, use an Abbott-sponsored third-party API, or authenticate to Abbott.

LibreLink alone is **not confirmed** to write glucose data directly into Health Connect. A community-maintained bridge, [HealthConnect-LibreLinkUp](https://github.com/c99koder/HealthConnect-LibreLinkUp), states it can read **LibreLinkUp** follower data and write it into Health Connect approximately **every 15 minutes**. It is **not an Abbott product**, it may stop functioning after Abbott changes the service, and its GitHub description is *not* live test evidence from a Jarvis phone. Another non-official route is LibreLinkUp → Nightscout → custom connectors, but Jarvis does not currently have a Nightscout connector.

## Proposed private test: no Abbott credentials supplied to Jarvis

1. Confirm the exact Libre 2 / Libre 2 Plus sensor, Android handset and official **FreeStyle LibreLink GB** app are supported by Abbott. Start the sensor using the official app and allow its specified warm-up. Do not uninstall the app or suppress its alarms.
2. In LibreLink, use **Connected Apps / LibreLinkUp** to invite an authorised follower account. Sign into the official LibreLinkUp app under the follower account and confirm that it actually receives timestamped glucose values from the sensor over the network.
3. Separately review the source, publisher and install/security implications of **HealthConnect-LibreLinkUp** before considering an installation. It is an **optional, unofficial experiment**, not bundled with Jarvis or an Abbott-approved part of its release. The bridge requests the follower account's credentials; these should **never** be given to Jarvis or committed to GitHub. A safer long-term commercial solution would use an Abbott-authorised agreement/API if available.
4. If opting in to the bridge, configure it with the follower account, the correct Abbott region, and grant the bridge **write blood glucose** access in Health Connect. Grant **Jarvis read blood glucose** access in Android/Health Connect.
5. In Jarvis → Vércukor → Beállítások → Health Connect, choose **Adatok ellenőrzése**. Jarvis should display the stored glucose value and timestamp. If none exists, report **no accessible records** rather than a fabricated reading.
6. Compare the timestamp, units and value with the official LibreLink/LibreLinkUp display. Repeat for a fresh reading and an intentionally disconnected/network-unavailable state. Ask Jarvis by voice “Mennyi most a vércukrom?” and verify it reports the record age accurately. **It must not call a 15-minute-old reading “live.”**

## Acceptance gates (not yet passed)

- [ ] Real sensor and official LibreLink GB paired and producing readings.
- [ ] LibreLinkUp follower receives the same values, correct region and timestamps.
- [ ] User-approved third-party bridge actually writes `BloodGlucoseRecord` to Health Connect.
- [ ] Jarvis on a real Android phone reads only authorised, timestamped records.
- [ ] Tests include denied permission, zero records, >10-minute stale records, offline, mmol/L and mg/dL, voice, revocation and recovery.
- [ ] Third-party credentials and personal glucose history are never sent to the Jarvis AI provider, logs, GitHub or error reports.
- [ ] Product/legal/data-protection review before declaring the unofficial bridge an available **public/commercial** integration.

**Safety:** Jarvis glucose display is a secondary convenience; Abbott's official app, approved alarms, and medical guidance remain authoritative. Sensor glucose can differ from blood glucose, and readings should be confirmed by finger-prick when symptoms and results disagree.

## Sources

- Abbott UK LibreLinkUp official sharing: https://www.freestyle.abbott/uk-en/managing-your-diabetes/start-your-journey/librelinkup.html
- Abbott UK LibreView sync: https://pro.freestyle.abbott/uk-en/help-support/faq/question-answer.html?q=FreeStyle+LibreLinkquestion-104
- Android Health Connect permissions: https://support.google.com/android/answer/12201227
- Community connector (**unofficial**): https://github.com/c99koder/HealthConnect-LibreLinkUp
- Nightscout (**unofficial** LibreLinkUp pathway): https://github.com/nightscout/nightscout-connect

## Source note

The current `JarvisHealthGlucose.latest` returns the latest permitted Health Connect record **from any authorised publisher**, not proof that a specific record originated from LibreLink. A test must inspect the Health Connect source app and compare values before making provenance claims.
