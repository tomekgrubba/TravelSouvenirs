package com.travelsouvenirs.main.platform

import platform.darwin.NSObject
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView

internal class OsmNavigationDelegate(
    private val onPageReady: () -> Unit
) : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onPageReady()
    }
}
