# Stage 1: Build
FROM --platform=$BUILDPLATFORM mcr.microsoft.com/dotnet/sdk:8.0 AS build

WORKDIR /app

# Copy only the .csproj file and restore dependencies
COPY *.csproj ./
RUN dotnet restore

# Copy the remaining code and publish the application
COPY . ./
RUN dotnet publish --configuration Release --output out

# Stage 2: Runtime
FROM --platform=$TARGETPLATFORM mcr.microsoft.com/dotnet/runtime:8.0

WORKDIR /app

# Copiar os artefatos publicados da etapa de build
COPY --from=build /app/out ./

# Define the entrypoint to run the application
ENTRYPOINT ["dotnet", "MqttConsoleClient.dll"]
