#!/usr/bin/env python3
import sys
import json
import threading
from concurrent.futures import ThreadPoolExecutor

try:
    import yt_dlp
except Exception as e:
    sys.stderr.write(f"Failed to import yt_dlp: {e}\n")
    sys.exit(1)

quality_formats = {
    'high': 'bestaudio[abr>=192]/bestaudio/best',
    'medium': 'bestaudio[abr<=192]/bestaudio/best',
    'low': 'worstaudio/bestaudio[abr<=96]/bestaudio/best',
}

stdout_lock = threading.Lock()
executor = ThreadPoolExecutor(max_workers=4, thread_name_prefix="ResolverWorker")
cancelled_ids = set()
cancelled_lock = threading.Lock()

# Thread-local storage for YoutubeDL instances to ensure thread-safety while reusing sessions
thread_local = threading.local()

def get_ydl_for_thread(quality='medium'):
    if not hasattr(thread_local, 'instances'):
        thread_local.instances = {}
    if quality not in thread_local.instances:
        fmt = quality_formats.get(quality, 'bestaudio[abr<=192]/bestaudio/best')
        thread_local.instances[quality] = yt_dlp.YoutubeDL({
            'quiet': True,
            'no_warnings': True,
            'format': fmt,
            'extract_flat': False,
            'skip_download': True,
            'youtube_include_dash_manifest': False,
            'youtube_include_hls_manifest': False,
            'socket_timeout': 8,
            'nocheckcertificate': True,
        })
    return thread_local.instances[quality]

def send_response(data):
    msg = json.dumps(data) + "\n"
    with stdout_lock:
        sys.stdout.write(msg)
        sys.stdout.flush()

def process_request(req_id, video_id, quality):
    with cancelled_lock:
        if req_id in cancelled_ids:
            cancelled_ids.discard(req_id)
            return

    # Validate YouTube video ID format to avoid wasted network calls
    if not video_id or len(video_id) < 8 or video_id.startswith(('local_', 'demo_', 'mock-', 'fb-', 'quick-')):
        send_response({'id': req_id, 'error': 'Invalid or non-YouTube ID'})
        return

    try:
        ydl = get_ydl_for_thread(quality)
        url = video_id if video_id.startswith('http') else f"https://www.youtube.com/watch?v={video_id}"
        info = ydl.extract_info(url, download=False)
        stream_url = info.get('url') if info else None
        if not stream_url and info and 'formats' in info:
            for f in reversed(info['formats']):
                if f.get('acodec') != 'none' and f.get('url'):
                    stream_url = f['url']
                    break

        with cancelled_lock:
            if req_id in cancelled_ids:
                cancelled_ids.discard(req_id)
                return

        send_response({'id': req_id, 'url': stream_url})
    except Exception as err:
        with cancelled_lock:
            if req_id in cancelled_ids:
                cancelled_ids.discard(req_id)
                return
        send_response({'id': req_id, 'error': str(err)})

sys.stdout.write("READY\n")
sys.stdout.flush()

for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    try:
        req = json.loads(line)
        if 'cancel' in req:
            with cancelled_lock:
                cancelled_ids.add(req['cancel'])
            continue

        req_id = req.get('id')
        video_id = req.get('videoId')
        quality = req.get('quality', 'medium')

        executor.submit(process_request, req_id, video_id, quality)
    except Exception as err:
        pass
