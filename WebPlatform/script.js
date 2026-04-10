// ==========================================
// 1. CONFIGURAÇÕES E ESTADO GLOBAL
// ==========================================
const SERVER_STREAM_URL = "http://85.xxx.xxx.xxx:5000/api/stream"; // <--- ALTERA ISTO PARA O TEU IP

let currentMode = 'live';
let routeData = [];
let currentLat = null;
let currentLon = null;
let lastLat = null;
let lastLon = null;
let ultimoAlertaWeb = "NONE";

let liveState = {
    roll: 0, vel: 0, bat: '--', lat: null, lon: null
};

// ==========================================
// 2. MOTOR DO MAPA (LEAFLET)
// ==========================================
const motoIcon = L.divIcon({
    html: `<img id="moto-marker-icon" src="https://cdn-icons-png.flaticon.com/128/11432/11432322.png" style="width: 32px; height: 32px; transition: transform 1s linear; transform: rotate(180deg);">`,
    className: 'custom-moto-icon',
    iconSize: [32, 32],
    iconAnchor: [16, 16]
});

let map = L.map('map', { zoomControl: false }).setView([41.3, -7.74], 15);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, theme: 'dark' }).addTo(map);
let marker = L.marker([41.3, -7.74], {icon: motoIcon}).addTo(map);

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
    if (currentLat !== null && currentLon !== null && currentLat !== 0) {
        let newLatLng = new L.LatLng(currentLat, currentLon);
        marker.setLatLng(newLatLng);
        
        map.panTo(newLatLng, { animate: true, duration: 1.0, easeLinearity: 1 });

        if (lastLat !== null && lastLon !== null && (lastLat !== currentLat || lastLon !== currentLon)) {
            let angle = calculateBearing(lastLat, lastLon, currentLat, currentLon); 
            document.getElementById('moto-marker-icon').style.transform = `rotate(${angle - 90}deg)`;
        }
        lastLat = currentLat; lastLon = currentLon;
    }
}

// ==========================================
// 3. GESTÃO DE INTERFACE (UI) E ALERTAS
// ==========================================
function switchMode(mode) {
    currentMode = mode;
    document.getElementById('btn-live').classList.toggle('active', mode === 'live');
    document.getElementById('btn-history').classList.toggle('active', mode === 'history');
    
    const liveCamWrapper = document.getElementById('live-cam-wrapper');            
    const histControls = document.getElementById('history-controls');
    const alertsList = document.getElementById('alerts-list');
    const historyMsg = document.getElementById('history-msg');
    const alertsTitle = document.getElementById('alerts-title');
    const headerStatus = document.querySelector('.header-status'); 
    const fcwCard = document.querySelector('.fcw-card');
    const rainCard = document.querySelector('.rain-card');

    if (mode === 'history') {
        liveCamWrapper.style.display = 'none';
        histControls.style.display = 'flex';
        headerStatus.style.display = 'none'; 
        alertsList.style.display = 'none';
        historyMsg.style.display = 'block';
        alertsTitle.innerText = "Histórico de Rota";
        if (fcwCard) fcwCard.style.display = 'none';
        if (rainCard) rainCard.style.display = 'none';
        fetchRoutes();
    } else {
        histControls.style.display = 'none';
        liveCamWrapper.style.display = 'block';
        headerStatus.style.display = 'flex'; 
        document.getElementById('route-video').pause();
        if(vPlaying) toggleVirtualPlay();
        if (fcwCard) fcwCard.style.display = 'block'; 
        if (rainCard) rainCard.style.display = 'block';
        
        alertsList.style.display = 'block';
        historyMsg.style.display = 'none';
        alertsTitle.innerText = "Eventos Recentes";

        // Restaura a UI para o último estado conhecido
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
        case 'RAIN': icon = 'fa-cloud-rain'; colorClass = 'alert-rain'; break;
        case 'FCW': icon = 'fa-solid fa-car'; colorClass = 'alert-info'; break;
        default: icon = 'fa-circle-info'; colorClass = 'alert-info';
    }

    const li = document.createElement('li');
    li.className = `alert-item ${colorClass}`;
    li.innerHTML = `<i class="fa-solid ${icon}"></i> 
                    <div><span>${message}</span> <span class="time">${time}</span></div>`;
    
    list.prepend(li);
    if (list.children.length > 30) list.lastChild.remove(); 
}

// ==========================================
// 4. INTEGRAÇÃO SSE (TEMPO REAL C#)
// ==========================================
function iniciarStreamServidor() {
    const status = document.getElementById("conn-status");
    status.innerText = "A ligar..."; 
    status.style.color = "var(--warning)";

    const eventSource = new EventSource(SERVER_STREAM_URL);

    eventSource.onopen = () => {
        status.innerText = "Online"; 
        status.style.color = "var(--primary)";
    };

    eventSource.onerror = () => {
        status.innerText = "Falha de Rede"; 
        status.style.color = "var(--danger)";
    };

    eventSource.onmessage = (event) => {
        if (currentMode === 'history') return;

        try {
            const data = JSON.parse(event.data);
            if (Object.keys(data).length === 0) return;

            // Memória
            if (data.r !== undefined) liveState.roll = data.r;
            if (data.spd !== undefined) liveState.vel = data.spd;
            if (data.soc !== undefined) liveState.bat = data.soc;
            if (data.lat !== undefined && data.lat !== 0) liveState.lat = data.lat;
            if (data.lon !== undefined && data.lon !== 0) liveState.lon = data.lon;

            // UI
            document.getElementById("vel-val").innerText = liveState.vel;
            document.getElementById("bat-val").innerText = liveState.bat;
            document.getElementById("roll-val").innerText = liveState.roll.toFixed(2);
            document.getElementById("bike-img").style.transform = `rotate(${liveState.roll}deg)`;

            const stateBadge = document.getElementById("moto-state");
            if (data.ign === true) {
                stateBadge.innerText = "ON"; stateBadge.classList.add('on');
            } else if (data.ign === false) {
                stateBadge.innerText = "OFF"; stateBadge.classList.remove('on');
            }

            // Mapa
            currentLat = liveState.lat; currentLon = liveState.lon;
            updateMap();

            // Alertas
            const alertType = data.alr || "NONE";
            if (alertType !== "NONE") {
                if (alertType === "RAIN_CLEARED") {
                    document.getElementById("rain-icon").classList.replace("active", "inactive");
                    document.getElementById("rain-text").innerText = "Seco";
                } else if (alertType !== ultimoAlertaWeb) {
                    ultimoAlertaWeb = alertType;
                    let msg = "Evento registado.";
                    if (alertType === "FALL") msg = "Queda detetada! Risco de acidente.";
                    if (alertType === "THEFT") msg = "Movimento suspeito! Possível roubo.";
                    if (alertType === "FCW") msg = "Perigo Iminente! Aviso de colisão frontal.";
                    if (alertType === "RAIN") msg = "Chuva detetada. Cubra a mota.";
                    
                    logEvent(alertType, msg);
                    setTimeout(() => { ultimoAlertaWeb = "NONE"; }, 10000);

                    if (alertType === "RAIN") {
                        document.getElementById("rain-icon").classList.remove("inactive", "active");
                        document.getElementById("rain-icon").classList.add("active");
                        document.getElementById("rain-text").innerText = "A Chover";
                    }
                }
            }

            // FCW (Se existir)
            if (data.fcw) {
                const dist = data.fcw.dist;
                const ttc = data.fcw.ttc;
                document.getElementById("fcw-dist").innerText = dist.toFixed(2);
                document.getElementById("fcw-ttc").innerText = ttc < 99 ? ttc.toFixed(1) : "--";
                
                const obstacleBar = document.getElementById("radar-obstacle");
                const safeDist = Math.min(dist, 4.0);
                obstacleBar.style.right = `${100 - ((safeDist / 4.0) * 100)}px`;
                
                if (dist > 3.80) obstacleBar.style.backgroundColor = "#00ff00";
                else if (dist > 1.50) obstacleBar.style.backgroundColor = "#ffa500";
                else obstacleBar.style.backgroundColor = "#ff0000";
            }
        } catch (e) {
            console.error("Erro a processar JSON:", e);
        }
    };
}

iniciarStreamServidor();

// ==========================================
// 5. MOTOR DE HISTÓRICO E SIMULAÇÃO
// ==========================================
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

    const videoElement = document.getElementById('route-video');
    const virtualPlayer = document.getElementById('virtual-player');

    const totalSecs = Math.round(route.duration_sec);
    const mins = Math.floor(totalSecs / 60);
    const secs = (totalSecs % 60).toString().padStart(2, '0');

    clearInterval(vTimer);
    vPlaying = false;
    vTimeMs = 0;
    document.getElementById('virtual-play-btn').innerHTML = '<i class="fa-solid fa-play"></i>';
    document.getElementById('route-stats').innerHTML = `Distância: ${route.distance_km} km | Duração: ${mins}:${secs}`;

    if (route.video_file) {
        videoElement.src = `./videos/${route.video_file.split('/').pop()}`;
        videoElement.style.display = 'block';
        virtualPlayer.style.display = 'none';
    } else {
        videoElement.src = "";
        videoElement.style.display = 'none';
        virtualPlayer.style.display = 'block';
        vDurationMs = route.duration_sec * 1000;
        updateVirtualUI();
    }

    const res = await fetch(`./data/${route.data_file}`);
    routeData = await res.json();
}

function syncTelemetry(currentTimeMs) {
    if(currentMode !== 'history' || routeData.length === 0) return;
    
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
}

document.getElementById('route-video').addEventListener('timeupdate', (e) => {
    const currentTimeMs = Math.floor(e.target.currentTime * 10) * 100;
    syncTelemetry(currentTimeMs);
});

let vTimer = null;
let vTimeMs = 0;
let vDurationMs = 0;
let vPlaying = false;

function formatVirtualTime(ms) {
    let totalSecs = Math.floor(ms / 1000);
    let mins = Math.floor(totalSecs / 60);
    let secs = (totalSecs % 60).toString().padStart(2, '0');
    return `${mins}:${secs}`;
}

function updateVirtualUI() {
    document.getElementById('virtual-slider').value = (vTimeMs / vDurationMs) * 100 || 0;
    document.getElementById('virtual-time-display').innerText = `${formatVirtualTime(vTimeMs)} / ${formatVirtualTime(vDurationMs)}`;
}

function toggleVirtualPlay() {
    vPlaying = !vPlaying;
    const btn = document.getElementById('virtual-play-btn');
    if (vPlaying) {
        btn.innerHTML = '<i class="fa-solid fa-pause"></i>';
        vTimer = setInterval(() => {
            vTimeMs += 100;
            if (vTimeMs >= vDurationMs) {
                vTimeMs = vDurationMs;
                toggleVirtualPlay();
            }
            updateVirtualUI();
            syncTelemetry(vTimeMs);
        }, 100);
    } else {
        btn.innerHTML = '<i class="fa-solid fa-play"></i>';
        clearInterval(vTimer);
    }
}

function seekVirtual() {
    const sliderVal = document.getElementById('virtual-slider').value;
    vTimeMs = (sliderVal / 100) * vDurationMs;
    updateVirtualUI();
    syncTelemetry(vTimeMs);
}