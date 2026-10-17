import SwiftUI
import UIKit

private enum JarvisStyle {
    static let background = Color(red: 0.035, green: 0.055, blue: 0.098)
    static let panel = Color(red: 0.095, green: 0.137, blue: 0.204)
    static let cyan = Color(red: 0.375, green: 0.835, blue: 0.898)
    static let muted = Color(red: 0.71, green: 0.76, blue: 0.83)
}

struct Reading: Codable, Identifiable {
    var id = UUID()
    let value: Double
    let timestamp: Date
}
struct Invoice: Codable, Identifiable {
    var id = UUID()
    let client: String
    let amount: Double
    let timestamp: Date
}
struct SharedFile: Identifiable {
    let id = UUID()
    let url: URL
}

private enum LocalStore {
    static func load<T: Decodable>(_ key: String) -> [T] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([T].self, from: data)) ?? []
    }
    static func save<T: Encodable>(_ items: [T], key: String) {
        guard let data = try? JSONEncoder().encode(items) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }
}
private func timeString(_ date: Date) -> String {
    date.formatted(date: .abbreviated, time: .shortened)
}

private struct Panel<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(JarvisStyle.panel, in: RoundedRectangle(cornerRadius: 18))
    }
}

struct JarvisRootView: View {
    @State private var selectedTab = 0
    @State private var readings: [Reading] = LocalStore.load("readings")
    @State private var invoices: [Invoice] = LocalStore.load("invoices")
    @State private var notes: [String] = LocalStore.load("notes")
    @State private var glucoseInput = ""
    @State private var clientInput = ""
    @State private var amountInput = ""
    @State private var noteInput = ""
    @State private var chatInput = ""
    @State private var chatSent = false
    @State private var formMessage = ""
    @State private var sharedFile: SharedFile?

    var body: some View {
        TabView(selection: $selectedTab) {
            page(title: "JARVIS", subtitle: "MOBILE · BASIC · iOS") { home }
                .tabItem { Label("Főoldal", systemImage: "house") }.tag(0)
            page(title: "Jarvis chat", subtitle: "AI-kapcsolat előkészítve") { chat }
                .tabItem { Label("Chat", systemImage: "bubble.left.and.bubble.right") }.tag(1)
            page(title: "Vércukornapló", subtitle: "mmol/L · helyi mentés") { glucose }
                .tabItem { Label("Vércukor", systemImage: "heart.text.square") }.tag(2)
            page(title: "Számlák", subtitle: "Helyi vázlatok és PDF") { invoiceView }
                .tabItem { Label("Számlák", systemImage: "doc.text") }.tag(3)
            page(title: "Eszközök", subtitle: "Email, navigáció, jegyzet") { tools }
                .tabItem { Label("Továbbiak", systemImage: "square.grid.2x2") }.tag(4)
        }
        .tint(JarvisStyle.cyan)
        .sheet(item: $sharedFile) { file in
            ShareSheet(items: [file.url])
        }
    }

    private func page<Content: View>(
        title: String, subtitle: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        ZStack {
            JarvisStyle.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(title).font(.system(size: 26, weight: .heavy))
                                .foregroundStyle(JarvisStyle.cyan)
                            Text(subtitle).font(.system(size: 11))
                                .foregroundStyle(JarvisStyle.muted)
                        }
                        Spacer()
                        Text("● NATÍV").font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(.green)
                    }
                    content()
                }
                .padding(18)
            }
        }
    }

    private var home: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Üdv újra!").font(.title2.bold())
            Panel {
                Text("JARVIS BASIC").font(.headline).foregroundStyle(JarvisStyle.cyan)
                Text("Az önálló iPhone Jarvis alapverziója.").font(.subheadline)
                Text("A felhőalapú AI és PC-s szinkron még nincs bekötve.")
                    .font(.caption).foregroundStyle(JarvisStyle.muted)
            }
            HStack(spacing: 10) {
                overview("🩸 Vércukor", readings.count, tab: 2)
                overview("🧾 Számlák", invoices.count, tab: 3)
            }
            HStack(spacing: 10) {
                overview("💬 Chat", 0, tab: 1)
                overview("🧭 Eszközök", notes.count, tab: 4)
            }
            Panel {
                Text("Következő fejlesztések").font(.headline)
                Text("Központi AI, közös fiók, szinkronizáció, natív hangvezérlés, Libre 2 és OBD2 kapcsolatok.")
                    .font(.subheadline).foregroundStyle(JarvisStyle.muted)
            }
        }
    }

    private func overview(_ title: String, _ count: Int, tab: Int) -> some View {
        Button {
            selectedTab = tab
        } label: {
            VStack(alignment: .leading, spacing: 10) {
                Text(title).font(.subheadline.bold())
                Text(tab == 1 ? "Megnyitás" : "\(count) mentett")
                    .font(.caption).foregroundStyle(JarvisStyle.muted)
                Image(systemName: "arrow.up.right").foregroundStyle(JarvisStyle.cyan)
            }
            .frame(maxWidth: .infinity, minHeight: 84, alignment: .leading)
            .padding(12)
            .background(JarvisStyle.panel, in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }

    private var chat: some View {
        Panel {
            Text("Az AI még nincs konfigurálva.").foregroundStyle(JarvisStyle.cyan)
            Text("Ez a képernyő nem szimulál modellválaszokat.")
                .font(.caption).foregroundStyle(JarvisStyle.muted)
            TextField("Írj Jarvisnak", text: $chatInput)
                .textFieldStyle(.roundedBorder)
            Button("Küldés") { chatSent = true }
                .buttonStyle(.borderedProminent).disabled(chatInput.isEmpty)
            if chatSent {
                Text("A biztonságos AI API bekötése a következő fejlesztési lépés.")
                    .font(.caption).foregroundStyle(JarvisStyle.muted)
            }
        }
    }

    private var glucose: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Ez napló, nem orvosi diagnosztikai eszköz.")
                .font(.caption).foregroundStyle(JarvisStyle.muted)
            Panel {
                TextField("Mért érték (mmol/L)", text: $glucoseInput)
                    .keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                Button("Mérés mentése") {
                    let text = glucoseInput.replacingOccurrences(of: ",", with: ".")
                    guard let value = Double(text), (1...35).contains(value) else {
                        formMessage = "Adj meg 1 és 35 közötti értéket."
                        return
                    }
                    readings.insert(Reading(value: value, timestamp: Date()), at: 0)
                    LocalStore.save(readings, key: "readings")
                    glucoseInput = ""
                    formMessage = ""
                }
                .buttonStyle(.borderedProminent)
                if !formMessage.isEmpty { Text(formMessage).foregroundStyle(.orange) }
            }
            ForEach(readings) { reading in
                Panel {
                    HStack {
                        VStack(alignment: .leading) {
                            Text("\(reading.value.formatted()) mmol/L").font(.headline)
                            Text(timeString(reading.timestamp))
                                .font(.caption).foregroundStyle(JarvisStyle.muted)
                        }
                        Spacer()
                        Button("Törlés", role: .destructive) {
                            readings.removeAll { $0.id == reading.id }
                            LocalStore.save(readings, key: "readings")
                        }
                    }
                }
            }
            if readings.isEmpty { Text("Még nincs mentett mérés.").foregroundStyle(JarvisStyle.muted) }
        }
    }

    private var invoiceView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Nem ellenőrzött éles UK VAT/számlázási megfelelőség.")
                .font(.caption).foregroundStyle(JarvisStyle.muted)
            Panel {
                TextField("Ügyfél neve", text: $clientInput).textFieldStyle(.roundedBorder)
                TextField("Összeg (£)", text: $amountInput)
                    .keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                Button("Számlavázlat mentése") {
                    let text = amountInput.replacingOccurrences(of: ",", with: ".")
                    guard !clientInput.trimmingCharacters(in: .whitespaces).isEmpty,
                          let amount = Double(text), amount > 0, amount <= 1_000_000 else {
                        formMessage = "Adj meg ügyfelet és érvényes összeget."
                        return
                    }
                    invoices.insert(Invoice(client: clientInput, amount: amount, timestamp: Date()), at: 0)
                    LocalStore.save(invoices, key: "invoices")
                    clientInput = ""; amountInput = ""; formMessage = ""
                }
                .buttonStyle(.borderedProminent)
                if !formMessage.isEmpty { Text(formMessage).foregroundStyle(.orange) }
            }
            ForEach(invoices) { invoice in
                Panel {
                    Text(invoice.client).font(.headline)
                    Text("£\(invoice.amount.formatted(.number.precision(.fractionLength(2)))) · \(timeString(invoice.timestamp))")
                        .font(.caption).foregroundStyle(JarvisStyle.muted)
                    HStack {
                        Button("PDF megosztás") {
                            if let url = makePDF(invoice) { sharedFile = SharedFile(url: url) }
                        }
                        Button("Törlés", role: .destructive) {
                            invoices.removeAll { $0.id == invoice.id }
                            LocalStore.save(invoices, key: "invoices")
                        }
                    }
                }
            }
        }
    }

    private var tools: some View {
        VStack(alignment: .leading, spacing: 14) {
            Panel {
                Text("📧 Email").font(.headline)
                Text("Megnyitja a telefon alapértelmezett emailalkalmazását.")
                    .font(.caption).foregroundStyle(JarvisStyle.muted)
                Button("Email írása") {
                    if let url = URL(string: "mailto:") { UIApplication.shared.open(url) }
                }
                .buttonStyle(.borderedProminent)
            }
            Panel {
                Text("🧭 Navigáció").font(.headline)
                Button("Térkép megnyitása") {
                    if let url = URL(string: "http://maps.apple.com/?q=Nearby") {
                        UIApplication.shared.open(url)
                    }
                }
            }
            Panel {
                Text("🧠 Helyi jegyzetek").font(.headline)
                TextField("Új jegyzet", text: $noteInput).textFieldStyle(.roundedBorder)
                Button("Mentés") {
                    guard !noteInput.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                    notes.insert(noteInput, at: 0)
                    LocalStore.save(notes, key: "notes")
                    noteInput = ""
                }
                .buttonStyle(.borderedProminent)
                ForEach(Array(notes.enumerated()), id: \.offset) { i, note in
                    HStack {
                        Text(note).frame(maxWidth: .infinity, alignment: .leading)
                        Button("Törlés", role: .destructive) {
                            if notes.indices.contains(i) {
                                notes.remove(at: i)
                                LocalStore.save(notes, key: "notes")
                            }
                        }
                    }
                }
            }
            Panel {
                Text("🔒 Fejlesztés alatt").font(.headline)
                Text("AI, Gmail OAuth, Libre 2, OBD2, 42 nyelv, hangvezérlés és PC-s adatszinkron. A Windows Self-Repair nem része az iOS-verziónak.")
                    .font(.caption).foregroundStyle(JarvisStyle.muted)
            }
        }
    }

    private func makePDF(_ invoice: Invoice) -> URL? {
        let bounds = CGRect(x: 0, y: 0, width: 595, height: 842)
        let renderer = UIGraphicsPDFRenderer(bounds: bounds)
        let data = renderer.pdfData { context in
            context.beginPage()
            let style: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 16),
                .foregroundColor: UIColor.black
            ]
            ("JARVIS - INVOICE DRAFT" as NSString).draw(at: CGPoint(x: 42, y: 54), withAttributes: style)
            (("Customer: " + String(invoice.client.prefix(50))) as NSString)
                .draw(at: CGPoint(x: 42, y: 115), withAttributes: style)
            (("Amount: GBP " + invoice.amount.formatted(.number.precision(.fractionLength(2)))) as NSString)
                .draw(at: CGPoint(x: 42, y: 152), withAttributes: style)
            ("Draft only - verify legal and tax details." as NSString)
                .draw(at: CGPoint(x: 42, y: 210), withAttributes: style)
        }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("jarvis-draft-\(UUID().uuidString).pdf")
        do { try data.write(to: url); return url } catch { return nil }
    }
}

private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
