#!/bin/bash
set -e

VERSION=${1:-latest}
IMAGE_NAME="cripto-monitor"

echo "🔨 Building Cripto Monitor v${VERSION}..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker and try again."
    exit 1
fi

# Build production image
echo "📦 Building production image..."
docker build -t ${IMAGE_NAME}:${VERSION} .

# Tag as latest if not already latest
if [ "$VERSION" != "latest" ]; then
    docker tag ${IMAGE_NAME}:${VERSION} ${IMAGE_NAME}:latest
    echo "🏷️  Tagged: ${IMAGE_NAME}:${VERSION}"
    echo "🏷️  Tagged: ${IMAGE_NAME}:latest"
else
    echo "🏷️  Tagged: ${IMAGE_NAME}:latest"
fi

# Show image size
echo "📊 Image size:"
docker images ${IMAGE_NAME}:${VERSION} --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"

echo "✅ Build complete!"
echo ""
echo "🚀 To run the image:"
echo "   docker run -p 3000:3000 ${IMAGE_NAME}:${VERSION}"
echo ""
echo "🐳 To deploy with Docker Compose:"
echo "   docker-compose up -d"
echo ""
echo "📤 To push to registry:"
echo "   docker push ${IMAGE_NAME}:${VERSION}"
if [ "$VERSION" != "latest" ]; then
    echo "   docker push ${IMAGE_NAME}:latest"
fi
