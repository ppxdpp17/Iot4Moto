const BROKER_IP = "172.20.0.203"; 
const PORT = 9001;
const CLIENT_ID = "Web_Dash_" + Math.random().toString(16).substr(2, 5);

let currentMode = 'live';
let routeData = [];
let currentLat = null;
let currentLon = null;

let lastLat = null;
let lastLon = null;

//Icon da mota
const motoIcon = L.divIcon({
    html: `<img id="moto-marker-icon" src="https://cdn-icons-png.flaticon.com/128/11432/11432322.png" style="width: 32px; height: 32px; transition: transform 1s linear; transform: rotate(180deg);">`,
    className: 'custom-moto-icon',
    iconSize: [32, 32],
    iconAnchor: [16, 16]
});

// Memória do estado LIVE para restaurar quando voltamos do histórico
let liveState = {
    roll: 0, vel: 0, bat: '--', lat: null, lon: null
};

// INICIALIZAR MAPA
let map = L.map('map', { zoomControl: false }).setView([41.3, -7.74], 15);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, theme: 'dark' }).addTo(map);
let marker = L.marker([41.3, -7.74], {icon: motoIcon}).addTo(map);

// Função para calcular para onde a mota está virada
function calculateBearing(lat1, lon1, lat2, lon2) {
    const toRad = deg => deg * Math.PI / 180;
    const toDeg = rad => rad * 180 / Math.PI;
    const dLon = toRad(lon2 - lon1);
    lat1 = toRad(lat1); lat2 = toRad(lat2);
    const y = Math.sin(dLon) * Math.cos(lat2);
    const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
    return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

function updateMap() {
    if (currentLat !== null && currentLon !== null) {
        let newLatLng = new L.LatLng(currentLat, currentLon);
        marker.setLatLng(newLatLng);
        
        // Força a câmara do mapa a deslizar durante 1 segundo de forma linear
        map.panTo(newLatLng, {
            animate: true,
            duration: 1.0, 
            easeLinearity: 1
        });

        if (lastLat !== null && lastLon !== null && (lastLat !== currentLat || lastLon !== currentLon)) {
            let angle = calculateBearing(lastLat, lastLon, currentLat, currentLon);
            
            // Subtrai 90º para corrigir a orientação da imagem base
            document.getElementById('moto-marker-icon').style.transform = `rotate(${angle - 90}deg)`;
        }
        
        lastLat = currentLat;
        lastLon = currentLon;
    }
}

// ALTERNAR MODOS (LIVE / HISTÓRICO)
// ALTERNAR MODOS (LIVE / HISTÓRICO)
function switchMode(mode) {
    currentMode = mode;
    document.getElementById('btn-live').classList.toggle('active', mode === 'live');
    document.getElementById('btn-history').classList.toggle('active', mode === 'history');
    
    // Substitui a variável liveCam por esta:
    const liveCamWrapper = document.getElementById('live-cam-wrapper');            
    
    const histControls = document.getElementById('history-controls');
    const alertsList = document.getElementById('alerts-list');
    const historyMsg = document.getElementById('history-msg');
    const alertsTitle = document.getElementById('alerts-title');
    const headerStatus = document.querySelector('.header-status'); 

    if (mode === 'history') {
        liveCamWrapper.style.display = 'none'; // Atualizado aqui
        histControls.style.display = 'flex';
        headerStatus.style.display = 'none'; 
        alertsList.style.display = 'none';
        historyMsg.style.display = 'block';
        alertsTitle.innerText = "Histórico de Rota";
        
        fetchRoutes();
    } else {
        histControls.style.display = 'none';
        liveCamWrapper.style.display = 'block'; // Atualizado aqui
        headerStatus.style.display = 'flex'; 
        document.getElementById('route-video').pause();
        
        alertsList.style.display = 'block';
        historyMsg.style.display = 'none';
        alertsTitle.innerText = "Eventos Recentes";

        document.getElementById("vel-val").innerText = liveState.vel;
        document.getElementById("bat-val").innerText = liveState.bat;
        document.getElementById("roll-val").innerText = liveState.roll.toFixed(2);
        document.getElementById("bike-img").style.transform = `rotate(${liveState.roll}deg)`;
        
        currentLat = liveState.lat; currentLon = liveState.lon;
        updateMap();
    }
    setTimeout(() => map.invalidateSize(), 100);
}

function logEvent(type, message) {
    const list = document.getElementById('alerts-list');
    const time = new Date().toLocaleTimeString();
    let icon, colorClass;

    switch(type) {
        case 'FALL': icon = 'fa-triangle-exclamation'; colorClass = 'alert-danger'; break;
        case 'THEFT': icon = 'fa-shield-halved'; colorClass = 'alert-warning'; break;
        case 'RAIN': icon = 'fa-cloud-rain'; colorClass = 'alert-info'; break;
        default: icon = 'fa-circle-info'; colorClass = 'alert-info';
    }

    const li = document.createElement('li');
    li.className = `alert-item ${colorClass}`;
    li.innerHTML = `<i class="fa-solid ${icon}"></i> 
                    <div><span>${message}</span> <span class="time">${time}</span></div>`;
    
    list.prepend(li);
    if (list.children.length > 30) list.lastChild.remove(); 
}

// --- LIGAÇÃO MQTT ---
const client = new Paho.MQTT.Client(BROKER_IP, PORT, CLIENT_ID);

client.onConnectionLost = () => {
    const status = document.getElementById("conn-status");
    status.innerText = "Ligação Perdida"; status.style.color = "var(--danger)";
};

client.onMessageArrived = (message) => {
    const topic = message.destinationName;
    const payload = message.payloadString;

    try {
        if (topic === "moto/roll") liveState.roll = parseFloat(payload);
        if (topic === "moto/speed") liveState.vel = Math.round(parseFloat(payload));
        if (topic === "moto/battery") liveState.bat = Math.round(parseFloat(payload));
        if (topic === "moto/gps/latitude") liveState.lat = parseFloat(payload);
        if (topic === "moto/gps/longitude") liveState.lon = parseFloat(payload);

        if (topic === "moto/alarm") {
            const alertType = payload.trim().toUpperCase();
            let msg = "Evento registado.";
            if (alertType === "FALL") msg = "Queda detetada! Risco de acidente.";
            if (alertType === "THEFT") msg = "Movimento suspeito! Possível roubo.";
            if (alertType === "RAIN") msg = "Chuva detetada. Piso escorregadio.";
            logEvent(alertType, msg);
        }
        
        if (currentMode === 'history') return; 

        if (topic === "moto/roll") {
            document.getElementById("roll-val").innerText = liveState.roll.toFixed(2);
            document.getElementById("bike-img").style.transform = `rotate(${liveState.roll}deg)`;
        }
        else if (topic === "sim/motorcycle") {
            const stateBadge = document.getElementById("moto-state");
            if (payload === "on") {
                stateBadge.innerText = "ON"; stateBadge.classList.add('on');
            } else {
                stateBadge.innerText = "OFF"; stateBadge.classList.remove('on');
            }
        }
        else if (topic === "moto/speed") document.getElementById("vel-val").innerText = liveState.vel;
        else if (topic === "moto/battery") document.getElementById("bat-val").innerText = liveState.bat;
        else if (topic === "moto/gps/latitude" || topic === "moto/gps/longitude") {
            currentLat = liveState.lat; currentLon = liveState.lon; updateMap();
        }
    } catch (e) {}
};

client.connect({
    onSuccess: () => {
        const status = document.getElementById("conn-status");
        status.innerText = "Online"; status.style.color = "var(--primary)";
        
        client.subscribe("moto/roll");
        client.subscribe("sim/motorcycle");
        client.subscribe("moto/gps/latitude");
        client.subscribe("moto/gps/longitude");
        client.subscribe("moto/speed");
        client.subscribe("moto/battery");
        client.subscribe("moto/alarm");
    },
    onFailure: () => {
        const status = document.getElementById("conn-status");
        status.innerText = "Falha de Rede"; status.style.color = "var(--danger)";
    },
    useSSL: false, timeout: 3
});

// --- HISTÓRICO LÓGICA ---
async function fetchRoutes() {
    try {
        const res = await fetch('./data/index.json');
        const routes = await res.json();
        const select = document.getElementById('route-select');
        select.innerHTML = '<option value="">Selecione uma rota...</option>';
        window.routesCatalog = routes; 
        routes.forEach(r => select.innerHTML += `<option value="${r.id}">${r.name}</option>`);
    } catch (e) {}
}

async function loadRoute() {
    const id = document.getElementById('route-select').value;
    if(!id) return;
    const route = window.routesCatalog.find(r => r.id === id);

    const totalSecs = Math.round(route.duration_sec);
    const mins = Math.floor(totalSecs / 60);
    const secs = (totalSecs % 60).toString().padStart(2, '0');

    document.getElementById('route-stats').innerHTML = 
        `Distância: ${route.distance_km} km | Duração: ${mins}:${secs}`;

        
    const videoElement = document.getElementById('route-video');
    if (route.video_file) {
        videoElement.src = `./videos/${route.video_file.split('/').pop()}`;
        videoElement.style.display = 'block';
    } else {
        videoElement.style.display = 'none';
    }

    const res = await fetch(`./data/${route.data_file}`);
    routeData = await res.json();
}

document.getElementById('route-video').addEventListener('timeupdate', (e) => {
    if(currentMode !== 'history' || routeData.length === 0) return;
    
    const currentTimeMs = Math.floor(e.target.currentTime * 10) * 100;
    const frame = routeData.find(d => d.t >= currentTimeMs);
    
    if(frame) {
        document.getElementById("roll-val").innerText = frame.r.toFixed(2);
        document.getElementById("bike-img").style.transform = `rotate(${frame.r}deg)`;
        
        let speed = frame.speed || frame.v || frame.s || frame.vel || 0;
        document.getElementById("vel-val").innerText = Math.round(speed);
        
        document.getElementById("bat-val").innerText = frame.bat !== undefined ? Math.round(frame.bat) : "--";

        if(frame.la && frame.lo) {
            currentLat = frame.la; currentLon = frame.lo;
            updateMap();
        }
    }
});