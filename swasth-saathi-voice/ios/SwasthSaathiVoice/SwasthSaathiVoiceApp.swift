import SwiftUI

@main
struct SwasthSaathiVoiceApp: App {
    @StateObject private var vm = AppViewModel()
    var body: some Scene {
        WindowGroup {
            NavigationStack {
                VoiceView()
                    .navigationTitle("Swasth Saathi")
                    .toolbar { ToolbarItem(placement: .primaryAction) { NavigationLink("Notebook") { NotebookView() } } }
            }.environmentObject(vm)
        }
    }
}
