# Contributing to Smart DB Failover API

First off, thank you for considering contributing to the Smart DB Failover API! It's people like you that make the open-source community such a great place to learn, inspire, and create.

## Getting Started

1. **Fork the repository** on GitHub.
2. **Clone the project** to your own machine.
3. **Copy the environment template**: `cp .env.example .env` and adjust the credentials if needed.
4. **Start the local databases**: `docker-compose up -d`.
5. **Run the application**: `mvn spring-boot:run`.

## How to Contribute

- **Report a bug** or request a feature by opening an issue.
- **Submit a Pull Request** with your proposed changes:
  1. Create a branch (`git checkout -b feature/AmazingFeature`).
  2. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
  3. Push to the branch (`git push origin feature/AmazingFeature`).
  4. Open a Pull Request.

### Code Style
- We follow standard Java conventions.
- Make sure to write or update documentation along with your code changes.
- Ensure the project builds successfully with `mvn clean install` before submitting a PR.
