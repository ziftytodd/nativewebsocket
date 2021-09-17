var capacitorNativeWebsocket = (function (exports, core) {
	'use strict';

	const NativeWebsocket = core.registerPlugin('NativeWebsocket', {
	//web: () => import('./web').then(m => new m.NativeWebsocketWeb()),
	});

	exports.NativeWebsocket = NativeWebsocket;

	Object.defineProperty(exports, '__esModule', { value: true });

	return exports;

}({}, capacitorExports));
//# sourceMappingURL=plugin.js.map
