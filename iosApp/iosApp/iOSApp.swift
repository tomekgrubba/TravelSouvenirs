import SwiftUI
import FirebaseCore
import TOCropViewController
import ComposeApp

@main
struct iOSApp: App {
    init() {
        FirebaseApp.configure()
        MainViewControllerKt.doInitKoin()
        
        // Register the Swift image cropper implementation with Kotlin/Native
        ImageCropperRegistry.shared.cropper = ImageCropperImpl()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

class ImageCropperImpl: NSObject, PlatformImageCropper, TOCropViewControllerDelegate {
    
    private var onSuccess: ((String) -> Void)?
    private var onCancel: (() -> Void)?
    private var imagePath: String?
    
    func cropImage(imagePath: String, onSuccess: @escaping (String) -> Void, onCancel: @escaping () -> Void) {
        self.imagePath = imagePath
        self.onSuccess = onSuccess
        self.onCancel = onCancel
        
        guard let image = UIImage(contentsOfFile: imagePath) else {
            onCancel()
            return
        }
        
        guard let topViewController = activeRootViewController() else {
            onCancel()
            return
        }
        
        let cropViewController = TOCropViewController(image: image)
        cropViewController.delegate = self
        
        // Configure options to allow rotating and aspect ratio selections (same as uCrop on Android)
        cropViewController.aspectRatioPreset = .presetSquare // Default to square
        cropViewController.aspectRatioLockEnabled = false // Allow custom aspect ratios
        cropViewController.resetAspectRatioEnabled = true
        cropViewController.aspectRatioPickerButtonHidden = false
        
        topViewController.present(cropViewController, animated: true, completion: nil)
    }
    
    // MARK: - TOCropViewControllerDelegate
    
    func cropViewController(_ cropViewController: TOCropViewController, didCropTo image: UIImage, with cropRect: CGRect, angle: Int) {
        cropViewController.dismiss(animated: true) { [weak self] in
            guard let self = self, let onSuccess = self.onSuccess, let oldPath = self.imagePath else { return }
            
            let fileURL = URL(fileURLWithPath: oldPath)
            let dir = fileURL.deletingLastPathComponent()
            let filename = "cropped_" + fileURL.lastPathComponent
            let destURL = dir.appendingPathComponent(filename)
            
            // Compress with quality to JPEG representation matching app rules
            if let data = image.jpegData(compressionQuality: 0.9) {
                do {
                    try data.write(to: destURL)
                    onSuccess(destURL.path)
                } catch {
                    self.onCancel?()
                }
            } else {
                self.onCancel?()
            }
        }
    }
    
    func cropViewController(_ cropViewController: TOCropViewController, didFinishCancelled cancelled: Bool) {
        cropViewController.dismiss(animated: true) { [weak self] in
            self?.onCancel?()
        }
    }
    
    private func activeRootViewController() -> UIViewController? {
        return UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }?
            .rootViewController
    }
}
