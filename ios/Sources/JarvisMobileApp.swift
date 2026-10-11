import SwiftUI

@main
struct JarvisMobileApp: App {
    var body: some Scene {
        WindowGroup {
            JarvisRootView()
                .preferredColorScheme(.dark)
        }
    }
}
