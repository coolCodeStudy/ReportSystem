# How to Run ReportSystem

## Prerequisites
- **Java 17**: The project is configured for compatibility with Java 17.
- **Gradle**: A local compatible version of Gradle (7.6.4) has been downloaded to the project directory to ensure compatibility.

## Running the Application

1. Open your terminal in the project directory:
   ```bash
   cd /Users/lishaocheng/code/ReportSystem
   ```

2. Run the application using the local Gradle binary:
   ```bash
   ./gradlew bootRun
   ```

3. Once the application starts (you will see "Started ReportSystemApplicationKt"), open your browser and visit:
   [http://localhost:18080](http://localhost:18080)

By default, local `bootRun` uses port `18080` to avoid conflicts with other repositories that commonly use `8080`.
To use another local port temporarily:
   ```bash
   ./gradlew bootRun -PlocalPort=18081
   ```

## Stopping the Application
To stop the application, press `Ctrl + C` in the terminal window where it is running.

## Troubleshooting
If you encounter issues with the Gradle wrapper, try running `./gradlew wrapper` to regenerate it.
