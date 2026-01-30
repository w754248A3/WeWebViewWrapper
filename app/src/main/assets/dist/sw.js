"use strict";
const myself = self;
const CACHE_NAME = `temperature-converter-v1`;
// Use the install event to pre-cache all initial resources.
myself.addEventListener('install', event => {
    event.waitUntil((async () => {
        const cache = await caches.open(CACHE_NAME);
        cache.addAll([
            './',
            './icon512.png',
            './main.js',
            './main.css',
            './gen_password/gen_password.css',
            './gen_password/gen_password.html',
            './gen_password/gen_password.js',
        ]);
    })());
});
let currentFileData = null;
const channel = new BroadcastChannel('app-channel_7EAp3pAs');
channel.onmessage = (event) => {
    if (event.data && event.data.type === 'VIDEO_FILE_DATA') {
        console.log('Update UI:', event.data);
        currentFileData = event.data;
        channel.postMessage({
            type: "READY_TO_PLAY",
            virtualUrl: currentFileData.virtualUrl
        });
    }
};
const log = (mes) => {
    channel.postMessage({
        type: "LOG_MESSAGE",
        message: mes
    });
};
myself.addEventListener('fetch', event => {
    console.log("Fetch intercepted for:", event.request.url);
    if (currentFileData && currentFileData.virtualUrl && event.request.url.endsWith(currentFileData.virtualUrl)) {
        console.log("Intercept video request:", event.request.url);
        const p = handleVideoRequest(event.request, currentFileData.file);
        p.catch(err => { log(`Error handling video request: ${JSON.stringify(err)}`); });
        event.respondWith(p);
    }
    else {
        event.respondWith((async () => {
            const cache = await caches.open(CACHE_NAME);
            const load = async () => {
                try {
                    // If the resource was not in the cache, try the network.
                    const fetchResponse = await fetch(event.request);
                    cache.put(event.request, fetchResponse.clone());
                    return fetchResponse;
                    // Save the resource in the cache and return it.
                }
                catch {
                    return Response.error();
                }
            };
            // Get the resource from the cache.
            const cachedResponse = await cache.match(event.request.clone());
            if (cachedResponse) {
                load();
                return cachedResponse;
            }
            else {
                return await load();
            }
        })());
    }
});
// 3. 处理视频流请求
async function handleVideoRequest(request, currentFile) {
    if (!currentFile) {
        console.log("File not selected");
        return new Response("File not selected", { status: 404 });
    }
    const fileSize = currentFile.size;
    const rangeHeader = request.headers.get('Range');
    // 解析 Range 头 (例如: bytes=0-1048575)
    // 视频播放器通常会分段请求，而不是一次请求所有
    let start = 0;
    let end = fileSize - 1;
    if (rangeHeader) {
        const parts = rangeHeader.replace(/bytes=/, "").split("-");
        start = parseInt(parts[0], 10);
        if (parts[1]) {
            end = parseInt(parts[1], 10);
        }
    }
    // 计算 Content-Length
    const chunkSize = end - start + 1;
    // 4. 关键：切片读取原始文件
    // slice 是零拷贝的，性能很高
    const fileSlice = currentFile.slice(start, end + 1);
    // 5. 创建解密流 (位取反)
    const originalStream = fileSlice.stream();
    const decryptStream = new TransformStream({
        transform(chunk, controller) {
            // chunk 是 Uint8Array
            // 直接在内存中原地修改，速度极快
            for (let i = 0; i < chunk.length; i++) {
                // 按位取反 (~x)。在 Uint8Array 中，~0 (0xFF) 是一样的效果
                chunk[i] = ~chunk[i];
            }
            controller.enqueue(chunk);
        }
    });
    // 6. 返回 206 Partial Content
    // 浏览器会认为这是一个普通的网络视频流
    return new Response(originalStream.pipeThrough(decryptStream), {
        status: 206,
        headers: {
            'Content-Range': `bytes ${start}-${end}/${fileSize}`,
            'Accept-Ranges': 'bytes',
            'Content-Length': chunkSize.toString(),
            'Content-Type': 'video/mp4'
        }
    });
}
//# sourceMappingURL=sw.js.map