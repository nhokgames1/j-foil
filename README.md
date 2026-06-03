# J-Foil 🚀

[![Java Version](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square)](https://www.oracle.com/java/technologies/downloads/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen.svg?style=flat-square)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

**J-Foil** is a standalone, lightweight, and high-performance Nintendo Switch Tinfoil shop server written in Java 21 and Spring Boot 3. It serves games directly to the Switch running Tinfoil, backed by a clean Bootstrap admin dashboard for managing users and monitoring statistics.

---

## ✨ Features

- 🎮 **Tinfoil Protocol Integration**: Securely exposes `/tinfoil/index.json` to stream and organize NSP/XCI/NSZ files.
- 🔒 **User & Subscription Management**: Robust administrative UI to manage credentials, reset passwords, track user activity, and enable/disable subscriptions.
- ⚡ **High Performance**:
  - Leverages **Java 21 Virtual Threads** for non-blocking I/O operations.
  - Caches directory listings and statistics using **Caffeine Cache** to ensure instantaneous Tinfoil loading times.
- 🐳 **Docker-Ready**: Easy PostgreSQL database and server configuration via Docker Compose.
- 🎨 **Sleek Admin UI**: Modern, responsive admin dashboard to monitor download statistics, system logs, and user states.

---

## 🛠️ Tech Stack

- **Core**: Java 21, Spring Boot 3.2.4
- **Database**: PostgreSQL (JPA/Hibernate)
- **Security**: Spring Security (Form authentication for Admins, Basic Authentication for Tinfoil clients)
- **Templates**: Thymeleaf & Bootstrap 5
- **Caching**: Caffeine Cache

---

## 📦 Getting Started

### Prerequisites

- **Java Development Kit (JDK) 21** or higher
- **Maven 3.8+**
- **PostgreSQL** database (if running locally without Docker)

### Running Locally

1. **Configure the Database**
   Set the PostgreSQL database configuration in `src/main/resources/application.yml` or through environment variables.
   
2. **Launch J-Foil**
   Use Maven to run the application:
   ```bash
   mvn spring-boot:run
   ```
   The application will be running on `http://localhost:8080`.

### Running with Docker Compose

J-Foil is configured to launch with PostgreSQL automatically:
```bash
docker-compose up --build -d
```

Your J-Foil server will start on port `8080`, mounting the `./games` directory for your Switch files.

---

## ⚙️ Configuration & Environment Variables

You can customize J-Foil behavior by passing the following environment variables:

| Variable | Description | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC Connection URL | `jdbc:postgresql://localhost:5432/jfoil` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `jfoiluser` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `jfoilpassword` |
| `JFOIL_STORAGE_PATH` | Path where NSP/XCI files are stored | `./games` |
| `SERVER_PORT` | HTTP Server port | `8080` |

---

## 🎮 Tinfoil Connection Guide

To connect a Switch running Tinfoil to your J-Foil server:

1. Open **Tinfoil** on your Nintendo Switch.
2. Navigate to the **File Browser** tab on the left menu.
3. Press the **`(-)` (Minus)** button to add a new network location.
4. Configure the protocol with the following parameters:
   - **Protocol**: `http` (or `https` if you set up an SSL reverse proxy)
   - **Host**: Your server IP address / Domain name
   - **Port**: `8080` (or your customized port)
   - **Path**: `/tinfoil/` (Include the leading and trailing slash)
   - **Username**: `<User's Account Username>`
   - **Password**: `<User's Account Password>`
   - **Title**: `J-Foil Shop`
5. Press the **`(X)` (Save)** button.

Once added successfully, Tinfoil will authenticate and populate the tabs (New Games, Updates, DLC) with games located in your storage directory!

---

## 🛡️ License

This project is licensed under the MIT License - see the LICENSE file for details.
