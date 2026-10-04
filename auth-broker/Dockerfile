FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/nexora-auth-broker .

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/nexora-auth-broker /nexora-auth-broker
EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["/nexora-auth-broker"]
