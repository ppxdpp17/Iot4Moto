#Imagem oficial da Microsoft para compilar o código
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

#Copia o ficheiro de projeto e restaura as dependências (MQTT, gRPC)
COPY ["CustomBackend.csproj", "./"]
RUN dotnet restore "CustomBackend.csproj"

#Copia o resto do código e compila a versão final (Release)
COPY . .
RUN dotnet publish "CustomBackend.csproj" -c Release -o /app/publish /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS final
WORKDIR /app
EXPOSE 50051
EXPOSE 5000
COPY --from=build /app/publish .
ENTRYPOINT ["dotnet", "CustomBackend.dll"]
