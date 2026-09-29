#!/usr/bin/env python3
"""Статик-сервер фронта StaffMatch. Отдаёт без кеширования (удобно для разработки)."""
import http.server
import socketserver
import os

PORT = 8000
ROOT = os.path.dirname(os.path.abspath(__file__))


class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-store, no-cache, must-revalidate')
        self.send_header('Pragma', 'no-cache')
        self.send_header('Pragma', 'no-cache')
        super().end_headers()


class Handler(NoCacheHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)


if __name__ == '__main__':
    with socketserver.TCPServer(('127.0.0.1', PORT), Handler) as httpd:
        print(f'Serving {ROOT} on http://127.0.0.1:{PORT} (no-cache)')
        httpd.serve_forever()