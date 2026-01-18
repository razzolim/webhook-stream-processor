# webhook-stream-processor


## Context

Webhook Stream Processor is a lightweight service designed to receive webhook events from various sources, process them in real-time, 
and forward them to designated endpoints or services.

## Features
- **Real-time Processing**: Instantly process incoming webhook events. Each event is handled as it arrives.
- **Multiple Sources and Destinations**: Support for various webhook sources and the ability to forward events to multiple destinations.
- **Scalability**: Designed to handle varying loads of incoming webhooks efficiently.
- **Low Memory Footprint**: Optimized for minimal memory usage to ensure efficient performance.

## Feature Requirements
- Memory usage should be minimal to ensure efficient performance.
  - Low latency in processing and forwarding webhooks.
  - We are going to receive a list of events and process each event at a time.
  - Parsing each event should be done efficiently. Object Mapper may be used but should be optimized for performance.
- The service should be scalable to handle varying loads of incoming webhooks.
- Support for multiple webhook sources and destinations.

## Getting Started

### Prerequisites
- Java 21 or higher
- Gradle 9.2.1 or higher
- Docker