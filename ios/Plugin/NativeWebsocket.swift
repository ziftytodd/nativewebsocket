import Foundation

@objc public class NativeWebsocket: NSObject {
    @objc public func echo(_ value: String) -> String {
        return value
    }
}
