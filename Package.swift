// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "NativeWebsocket",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "NativeWebsocket",
            targets: ["NativeWebsocketPlugin"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0"),
        .package(url: "https://github.com/ziftytodd/Starscream.git", revision: "82be42f757c1e442bbc978fd473e27148ed9b0b1")
    ],
    targets: [
        .target(
            name: "NativeWebsocketPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                .product(name: "Starscream", package: "Starscream")
            ],
            path: "ios/Plugin",
            exclude: [
                "Info.plist",
                "NativeWebsocketPlugin.h",
                "NativeWebsocketPlugin.m"
            ],
            sources: [
                "NativeWebsocket.swift",
                "NativeWebsocketPlugin.swift"
            ]
        )
    ]
)
