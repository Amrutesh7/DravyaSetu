"""Streaming request-body limits for image uploads."""

from starlette.formparsers import MultiPartException


REQUEST_BODY_TOO_LARGE_DETAIL = (
    "Request body exceeds the upload size limit."
)


class RequestBodyTooLarge(MultiPartException):
    """Raised when a request exceeds the configured body limit."""

    def __init__(self):
        super().__init__(REQUEST_BODY_TOO_LARGE_DETAIL)


class UploadClientDisconnected(MultiPartException):
    """Raised so the multipart parser closes files on disconnect."""

    def __init__(self):
        super().__init__("Client disconnected during image upload.")


class AnalyzeRequestBodyLimitMiddleware:
    def __init__(
        self,
        app,
        max_bytes: int,
        path: str = "/ai/analyze",
    ):
        self.app = app
        self.max_bytes = max_bytes
        self.path = path

    async def __call__(self, scope, receive, send):
        if (
            scope["type"] != "http"
            or scope["method"] != "POST"
            or scope["path"] != self.path
        ):
            await self.app(scope, receive, send)
            return

        content_length = next(
            (
                value
                for name, value in scope.get("headers", [])
                if name.lower() == b"content-length"
            ),
            None,
        )
        if content_length is not None:
            try:
                declared_length = int(content_length)
            except ValueError:
                declared_length = None

            if declared_length is not None and declared_length > self.max_bytes:
                await self._send_too_large(send)
                return

        received_bytes = 0
        response_started = False
        body_complete = False
        content_type = next(
            (
                value
                for name, value in scope.get("headers", [])
                if name.lower() == b"content-type"
            ),
            b"",
        )
        is_multipart = content_type.lower().startswith(
            b"multipart/form-data"
        )

        async def limited_receive():
            nonlocal received_bytes, body_complete
            message = await receive()

            if message["type"] == "http.request":
                received_bytes += len(message.get("body", b""))
                if received_bytes > self.max_bytes:
                    raise RequestBodyTooLarge()
                if not message.get("more_body", False):
                    body_complete = True
            elif (
                message["type"] == "http.disconnect"
                and is_multipart
                and not body_complete
            ):
                raise UploadClientDisconnected()

            return message

        async def track_send(message):
            nonlocal response_started
            if message["type"] == "http.response.start":
                response_started = True
            await send(message)

        try:
            await self.app(scope, limited_receive, track_send)
        except RequestBodyTooLarge:
            if response_started:
                raise
            await self._send_too_large(send)

    async def _send_too_large(self, send):
        body = (
            f'{{"detail":"{REQUEST_BODY_TOO_LARGE_DETAIL}"}}'
        ).encode("ascii")
        await send(
            {
                "type": "http.response.start",
                "status": 413,
                "headers": [
                    (b"content-type", b"application/json"),
                    (b"content-length", str(len(body)).encode("ascii")),
                ],
            }
        )
        await send(
            {
                "type": "http.response.body",
                "body": body,
            }
        )
