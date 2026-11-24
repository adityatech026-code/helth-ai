import SwiftUI

private let teal = Color(red: 0.06, green: 0.43, blue: 0.34)
private func levelColor(_ l: String) -> Color { l == "emergency" ? .red : l == "see_doctor" ? .orange : l == "self_care" ? .green : .gray }

struct VoiceView: View {
    @EnvironmentObject var vm: AppViewModel
    @State private var typed = ""

    var body: some View {
        VStack(spacing: 8) {
            Picker("Language", selection: $vm.lang) { ForEach(Lang.allCases) { Text($0.label).tag($0) } }.pickerStyle(.segmented)
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(vm.messages) { Bubble(msg: $0).id($0.id) }
                        if !vm.partial.isEmpty { Bubble(msg: ChatMsg(fromUser: true, text: vm.partial + "…")) }
                    }
                }.onChange(of: vm.messages.count) { _ in if let id = vm.messages.last?.id { withAnimation { proxy.scrollTo(id, anchor: .bottom) } } }
            }
            HStack {
                TextField("Or type here", text: $typed).textFieldStyle(.roundedBorder)
                Button("Send") { if !typed.isEmpty { vm.handle(typed); typed = "" } }.buttonStyle(.borderedProminent)
            }
            Button(action: vm.toggleMic) {
                Image(systemName: vm.listening ? "stop.fill" : "mic.fill").font(.system(size: 30)).foregroundColor(.white)
                    .frame(width: 84, height: 84).background(vm.listening ? Color.red : teal).clipShape(Circle())
            }.accessibilityLabel(vm.listening ? "Stop listening" : "Start speaking")
        }.padding()
    }
}

struct Bubble: View {
    let msg: ChatMsg
    var body: some View {
        HStack {
            if msg.fromUser { Spacer(minLength: 40) }
            VStack(alignment: .leading, spacing: 4) {
                Text(msg.text).foregroundColor(msg.fromUser ? .white : .primary)
                if let r = msg.result { ResultCard(r: r) }
                if let c = msg.care { Text(c).font(.footnote) }
            }.padding(12).background(msg.fromUser ? teal : Color(.secondarySystemBackground)).cornerRadius(16)
            if !msg.fromUser { Spacer(minLength: 40) }
        }
    }
}

struct ResultCard: View {
    let r: TriageResult
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(r.level.replacingOccurrences(of: "_", with: " ").uppercased()).font(.caption.bold()).foregroundColor(levelColor(r.level))
            ForEach(r.selfCare, id: \.symptom) { s in
                Text("• \(s.symptom.replacingOccurrences(of: "_", with: " ")): \(s.drug)\n  \(s.dose)").font(.footnote)
                Text("Max: \(s.max)").font(.caption); Text("Water: \(s.hydration)").font(.caption)
                Text("Rest: \(s.rest)").font(.caption); Text("Diet: \(s.dietTip)").font(.caption)
            }
            if !r.tests.isEmpty { Text("Suggested tests: \(r.tests.joined(separator: ", "))").font(.footnote) }
            if !r.conditions.isEmpty { Text("Possible: " + r.conditions.prefix(3).map { "\($0.name) (\($0.icd10))" }.joined(separator: ", ")).font(.caption) }
            Text(r.disclaimer).font(.caption2).foregroundColor(.secondary).padding(.top, 4)
        }
    }
}

struct NotebookView: View {
    @EnvironmentObject var vm: AppViewModel
    @State private var lab = ""
    @State private var pdf: URL?
    private let df: DateFormatter = { let d = DateFormatter(); d.dateFormat = "d MMM, HH:mm"; return d }()

    var body: some View {
        List {
            Section("Progress") {
                if vm.trends.isEmpty { Text("Nothing logged yet.").foregroundColor(.secondary) }
                ForEach(vm.trends.prefix(3), id: \.symptom) { t in
                    VStack(alignment: .leading) {
                        Text(t.message)
                        Text("Last 4 weeks: " + t.weekly.map(String.init).joined(separator: " · ")).font(.caption).foregroundColor(.secondary)
                    }
                }
            }
            Section("Add lab result") {
                HStack { TextField("e.g. Hb 11.2, 24 Nov", text: $lab); Button("Save") { if !lab.isEmpty { vm.addLab(lab); lab = "" } } }
            }
            Section("Log") {
                ForEach(vm.entries) { e in
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(df.string(from: e.timestamp)) · \(e.level.replacingOccurrences(of: "_", with: " "))").font(.caption).foregroundColor(levelColor(e.level))
                        Text("“\(e.transcript)”")
                        if !e.recommendation.isEmpty { Text(e.recommendation).font(.caption).foregroundColor(.secondary) }
                    }
                }
            }
        }
        .navigationTitle("Health Notebook")
        .toolbar { ToolbarItem(placement: .primaryAction) {
            Button("Export PDF") { pdf = vm.exportPDF() }
        } }
        .sheet(isPresented: Binding(get: { pdf != nil }, set: { if !$0 { pdf = nil } })) { if let pdf { ShareSheet(items: [pdf]) } }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
