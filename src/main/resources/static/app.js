// Conquista: interfaz del juego. Toda la logica (reglas, algoritmos, IA) esta en
// el backend; aca solo se dibuja el estado y se llama a la API.
'use strict';

const NS = 'http://www.w3.org/2000/svg';
const FASES = ['RECLUTAMIENTO', 'MARCHA', 'ATAQUE', 'CIERRE'];
const GLIFO = { 'Ciudad': '♜', 'Bosque': '♣', 'Montaña': '▲', 'Llanura': '≈' };

let partida = null;          // la vista que devuelve la API
let porId = {};              // id -> territorio
let vecinos = {};            // id -> Set de ids vecinos
let seleccion = null;        // territorio propio elegido como origen
let objetivo = null;         // destino de la marcha / ataque en preparacion
let camino = [];             // ids a resaltar en el mapa (marcha o ruta)
let insignias = null;        // id -> numero a mostrar (alcance BFS)
let resaltados = new Set();  // territorios a resaltar (sugerencias, objetivos)
let mercado = null;          // respuesta de /mercado del turno

// ------------------------------------------------------------------ API

async function api(metodo, url, cuerpo) {
    const r = await fetch(url, {
        method: metodo,
        headers: cuerpo ? { 'Content-Type': 'application/json' } : {},
        body: cuerpo ? JSON.stringify(cuerpo) : undefined,
    });
    const datos = await r.json().catch(() => ({}));
    if (!r.ok) {
        throw new Error(datos.detalle || datos.error || ('Error ' + r.status));
    }
    return datos;
}

const base = () => '/api/juego/' + partida.id;

async function ejecutar(promesa) {
    try {
        return await promesa;
    } catch (e) {
        mostrarError(e.message);
        return null;
    }
}

function mostrarError(texto) {
    document.getElementById('accionContenido').innerHTML = `<p class="error">${esc(texto)}</p>`;
}

function esc(t) {
    return String(t).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

// ------------------------------------------------------------------ partida

document.getElementById('nueva').addEventListener('submit', async ev => {
    ev.preventDefault();
    const dificultad = document.getElementById('dificultad').value;
    const escenario = document.getElementById('escenario').value;
    const vista = await ejecutar(api('POST', `/api/juego?dificultad=${dificultad}&escenario=${escenario}`));
    if (!vista) {
        document.getElementById('portada').innerHTML =
            '<p class="error">No se pudo crear la partida. ¿Está corriendo el backend con Neo4j?</p>';
        return;
    }
    document.getElementById('portada').classList.add('oculto');
    document.getElementById('juego').classList.remove('oculto');
    document.getElementById('resultado').innerHTML = 'El resultado de cada algoritmo aparece acá.';
    actualizar(vista);
});

async function actualizar(vista) {
    partida = vista;
    porId = Object.fromEntries(vista.territorios.map(t => [t.id, t]));
    vecinos = {};
    for (const t of vista.territorios) vecinos[t.id] = new Set();
    for (const r of vista.rutas) {
        vecinos[r.origen].add(r.destino);
        vecinos[r.destino].add(r.origen);
    }
    seleccion = null;
    objetivo = null;
    camino = [];
    insignias = null;
    resaltados = new Set();
    mercado = null;
    if (miTurno() && vista.fase === 'RECLUTAMIENTO') {
        mercado = await ejecutar(api('GET', base() + '/mercado'));
    }
    dibujarTodo();
    document.getElementById('accionContenido').innerHTML =
        '<span class="tenue">Hacé clic en un territorio tuyo para marchar o atacar desde ahí.</span>';
}

const miTurno = () => partida && !partida.ganador && partida.turno === 'AZUL';
const fase = () => FASES.indexOf(partida.fase);

function dibujarTodo() {
    dibujarMapa();
    dibujarEstado();
    dibujarMercado();
    dibujarIa();
    dibujarLog();
    const terminar = document.getElementById('terminar');
    terminar.disabled = !miTurno();
    const banner = document.getElementById('banner');
    if (partida.ganador) {
        banner.classList.remove('oculto');
        banner.textContent = partida.ganador === 'EMPATE' ? 'Empate'
            : partida.ganador === 'AZUL' ? '¡Ganaste! Cayó la capital roja.' : 'Ganó la IA.';
    } else {
        banner.classList.add('oculto');
    }
}

// ------------------------------------------------------------------ mapa

function el(nombre, atributos, padre) {
    const n = document.createElementNS(NS, nombre);
    for (const [k, v] of Object.entries(atributos || {})) n.setAttribute(k, v);
    if (padre) padre.appendChild(n);
    return n;
}

function texto(contenido, atributos, padre) {
    const t = el('text', atributos, padre);
    t.textContent = contenido;
    return t;
}

const clave = (a, b) => (a < b ? a + '|' + b : b + '|' + a);

function dibujarMapa() {
    const svg = document.getElementById('mapa');
    svg.innerHTML = '';

    const red = new Map();
    for (const [nombre, j] of Object.entries(partida.jugadores)) {
        for (const a of j.red) red.set(clave(a.origen, a.destino), nombre);
    }
    const enCamino = new Set();
    for (let i = 1; i < camino.length; i++) enCamino.add(clave(camino[i - 1], camino[i]));

    const capaRutas = el('g', {}, svg);
    for (const r of partida.rutas) {
        const a = porId[r.origen], b = porId[r.destino];
        const k = clave(r.origen, r.destino);
        if (red.has(k)) {
            el('line', { x1: a.x, y1: a.y, x2: b.x, y2: b.y, class: 'ruta red-' + red.get(k) }, capaRutas);
        }
        if (enCamino.has(k)) {
            el('line', { x1: a.x, y1: a.y, x2: b.x, y2: b.y, class: 'ruta camino' }, capaRutas);
        }
        el('line', { x1: a.x, y1: a.y, x2: b.x, y2: b.y, class: 'ruta' + (r.costo >= 6 ? ' cara' : '') }, capaRutas);
        const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
        el('circle', { cx: mx, cy: my, r: 13, class: 'costo-fondo' }, capaRutas);
        texto(r.costo, { x: mx, y: my, class: 'costo' }, capaRutas);
    }

    for (const t of partida.territorios) {
        const clases = ['territorio', t.duenio];
        if (t.esCapital) clases.push('capital');
        if (t.duenio !== 'NEUTRAL' && !t.abastecido) clases.push('aislado');
        if (t.id === seleccion) clases.push('seleccionado');
        if (t.id === objetivo || resaltados.has(t.id)) clases.push('objetivo');
        const g = el('g', { class: clases.join(' '), tabindex: 0 }, svg);
        g.addEventListener('click', () => clicTerritorio(t.id));

        el('circle', { cx: t.x, cy: t.y, r: 46, class: 'halo' }, g);
        el('circle', { cx: t.x, cy: t.y, r: 36, class: 'cuerpo' }, g);
        texto(t.tropas, { x: t.x, y: t.y, class: 'tropas' }, g);
        if (t.esCapital) texto('♛', { x: t.x, y: t.y - 44, class: 'corona' }, g);
        texto(t.nombre + ' (' + t.id + ')', { x: t.x, y: t.y + 54, class: 'nombre' }, g);
        texto(`${GLIFO[t.terreno] || ''} ${t.terreno} · +${t.produccion} oro · def ${t.defensa}`,
            { x: t.x, y: t.y + 69, class: 'detalle' }, g);
        const titulo = el('title', {}, g);
        titulo.textContent = `${t.nombre}: ${t.duenio}, ${t.tropas} tropas, defensa ${t.defensa}`
            + (t.duenio !== 'NEUTRAL' && !t.abastecido ? ' (AISLADO)' : '');

        if (insignias && t.id in insignias) {
            const ins = el('g', { class: 'insignia' }, svg);
            el('circle', { cx: t.x + 30, cy: t.y - 30, r: 12 }, ins);
            texto(insignias[t.id], { x: t.x + 30, y: t.y - 30 }, ins);
        }
    }
}

// ------------------------------------------------------------------ clics: marcha y ataque

async function clicTerritorio(id) {
    const t = porId[id];
    if (!miTurno()) {
        return;
    }
    if (!seleccion || id === seleccion) {
        if (t.duenio === 'AZUL' && id !== seleccion) {
            seleccion = id;
            objetivo = null;
            camino = [];
            dibujarMapa();
            document.getElementById('accionContenido').innerHTML =
                `<b>${esc(t.nombre)}</b>: ${t.tropas} tropas. Ahora hacé clic en un territorio tuyo (marchar) `
                + `o en un vecino ajeno (atacar).`;
        } else {
            seleccion = null;
            objetivo = null;
            camino = [];
            dibujarMapa();
            document.getElementById('accionContenido').innerHTML = infoTerritorio(t);
        }
        return;
    }
    if (t.duenio === 'AZUL') {
        await prepararMarcha(seleccion, id);
    } else if (vecinos[seleccion].has(id)) {
        prepararAtaque(seleccion, id);
    } else {
        document.getElementById('accionContenido').innerHTML =
            `${infoTerritorio(t)}<p class="error">No es vecino de ${esc(porId[seleccion].nombre)}: solo se ataca por una ruta directa.</p>`;
    }
}

function infoTerritorio(t) {
    return `<b>${esc(t.nombre)}</b> (${t.id}) — ${t.duenio}<br>${t.terreno}, produce ${t.produccion} de oro<br>`
        + `${t.tropas} tropas, defensa ${t.defensa}${t.esCapital ? ' (capital)' : ''}`;
}

async function prepararMarcha(origen, destino) {
    objetivo = destino;
    const vista = await ejecutar(api('GET', `${base()}/marcha?origen=${origen}&destino=${destino}`));
    if (!vista) {
        camino = [];
        dibujarMapa();
        return;
    }
    camino = vista.vertices;
    dibujarMapa();
    const max = porId[origen].tropas - 1;
    const habilitado = fase() <= 1 && vista.alcanza && max >= 1;
    document.getElementById('accionContenido').innerHTML = `
        <b>Marcha</b> (Dijkstra por territorio propio): ${vista.vertices.join(' → ')}<br>
        <b>${vista.dias} días</b> de ${vista.puntosDeMarcha} disponibles
        ${vista.alcanza ? '' : '<span class="error">— no alcanza este turno</span>'}
        ${fase() > 1 ? '<p class="error">Ya pasó la fase de marcha.</p>' : ''}
        <div class="fila">
            <input id="cantidad" type="number" min="1" max="${Math.max(1, max)}" value="${Math.max(1, max)}" style="width:80px">
            <button id="hacer" ${habilitado ? '' : 'disabled'}>Marchar</button>
        </div>`;
    document.getElementById('hacer').onclick = async () => {
        const tropas = Number(document.getElementById('cantidad').value);
        const v = await ejecutar(api('POST', base() + '/marchar', { origen, destino, tropas }));
        if (v) actualizar(v);
    };
}

function prepararAtaque(origen, destino) {
    objetivo = destino;
    camino = [origen, destino];
    dibujarMapa();
    const o = porId[origen], d = porId[destino];
    const max = o.tropas - 1;
    const habilitado = fase() <= 2 && max >= 1;
    document.getElementById('accionContenido').innerHTML = `
        <b>Ataque</b> ${esc(o.nombre)} → ${esc(d.nombre)}<br>
        Defensa de ${esc(d.nombre)}: <b>${d.defensa}</b> (${d.tropas} tropas + ${d.terreno}${d.esCapital ? ' + capital' : ''})
        ${fase() > 2 ? '<p class="error">Ya atacaste este turno.</p>' : ''}
        <div class="fila">
            <input id="cantidad" type="number" min="1" max="${Math.max(1, max)}" value="${Math.max(1, max)}" style="width:80px">
            <button id="hacer" ${habilitado ? '' : 'disabled'}>Atacar</button>
        </div>
        <div id="prevision" class="compara"></div>`;
    const prever = () => {
        const n = Number(document.getElementById('cantidad').value);
        document.getElementById('prevision').innerHTML = n > d.defensa
            ? `Resultado: <b>conquista</b>, quedan ${n - d.defensa} tropas en ${d.id}.`
            : `Resultado: <span class="error">rechazado</span>: perdés ${n} y el defensor ${Math.floor(n / 2)}.`;
    };
    document.getElementById('cantidad').oninput = prever;
    prever();
    document.getElementById('hacer').onclick = async () => {
        const tropas = Number(document.getElementById('cantidad').value);
        const v = await ejecutar(api('POST', base() + '/atacar', { origen, destino, tropas }));
        if (v) actualizar(v);
    };
}

// ------------------------------------------------------------------ estado, mercado, IA, log

function dibujarEstado() {
    const j = partida.jugadores;
    const tarjeta = n => `
        <div class="jugador ${n}">
            <b>${n === 'AZUL' ? 'AZUL (vos)' : 'ROJO (IA)'}</b><br>
            Oro: <b>${j[n].oro}</b> · Territorios: ${j[n].territorios} · Tropas: ${j[n].tropas}<br>
            Ingreso: ${j[n].produccion} − ${j[n].mantenimiento} = <b>${j[n].ingresoNeto}</b>
            <span class="tenue">(red ${j[n].costoRed} días)</span>
            ${j[n].aislados.length ? `<br><span class="error">Aislados: ${j[n].aislados.join(', ')}</span>` : ''}
        </div>`;
    const fases = ['Compra', 'Marcha', 'Ataque', 'Cierre'].map((nombre, i) =>
        `<span class="${i === fase() ? 'activa' : i < fase() ? 'pasada' : ''}">${nombre}</span>`).join('');
    document.getElementById('estado').innerHTML = `
        <h2>Ronda ${partida.ronda} de ${partida.limiteRondas} · turno de ${partida.turno}
            <small>· IA ${partida.dificultad}</small></h2>
        <div class="fases">${fases}</div>
        <div class="marcador">${tarjeta('AZUL')}${tarjeta('ROJO')}</div>`;
}

function dibujarMercado() {
    const caja = document.getElementById('mercadoContenido');
    if (!miTurno() || partida.fase !== 'RECLUTAMIENTO' || !mercado) {
        caja.innerHTML = '<span class="tenue">El mercado abre al principio de tu turno.</span>';
        return;
    }
    const asequibles = new Set(mercado.asequibles.map(u => u.id));
    const abastecidos = partida.territorios.filter(t => t.duenio === 'AZUL' && t.abastecido);
    caja.innerHTML = `
        <div class="tenue">Oro: ${mercado.oro}. La búsqueda binaria corta el catálogo en
            ${mercado.ultimoAsequible >= 0 ? mercado.catalogoPorCosto[mercado.ultimoAsequible].nombre : 'ninguna'}.</div>
        ${mercado.catalogoPorCosto.map(u => `
            <label class="unidad ${asequibles.has(u.id) ? '' : 'no-alcanza'}">
                <input type="checkbox" value="${u.id}" ${asequibles.has(u.id) ? '' : 'disabled'}>
                <span>${esc(u.nombre)}</span><span>${u.peso} oro</span><span>+${u.valor}</span>
            </label>`).join('')}
        <div class="fila">
            <select id="destinoCompra">${abastecidos.map(t =>
                `<option value="${t.id}">${esc(t.nombre)}</option>`).join('')}</select>
            <button id="comprar">Comprar</button>
            <span id="totalCompra" class="tenue"></span>
        </div>
        <div class="fila">
            <button id="sugerirMochila">Sugerir (mochila 0/1)</button>
            <button id="sugerirGreedy">Greedy</button>
            <button id="repartir">Repartir en frentes (B&amp;B)</button>
        </div>
        <div id="comparaCompra" class="compara"></div>`;

    const casillas = () => [...caja.querySelectorAll('input[type=checkbox]')];
    const total = () => {
        let oro = 0, poder = 0;
        for (const c of casillas()) if (c.checked) {
            const u = mercado.catalogoPorCosto.find(x => x.id === c.value);
            oro += u.peso;
            poder += u.valor;
        }
        document.getElementById('totalCompra').textContent = `${oro} oro → +${poder}`;
        document.getElementById('totalCompra').className = oro > mercado.oro ? 'error' : 'tenue';
    };
    casillas().forEach(c => (c.onchange = total));
    total();
    const marcar = ids => {
        casillas().forEach(c => (c.checked = ids.includes(c.value)));
        total();
    };
    const resumen = r => `${r.elegidos.map(u => u.nombre).join(' + ') || 'nada'}: poder <b>${r.valorTotal}</b>, gasta ${r.pesoTotal}`;
    const comparar = () => {
        document.getElementById('comparaCompra').innerHTML =
            `Greedy (ratio poder/oro): ${resumen(mercado.greedy)}<br>Mochila 0/1 (PD): ${resumen(mercado.mochila)}`
            + (mercado.poderQuePierdeGreedy > 0 ? `<br><b>Greedy pierde ${mercado.poderQuePierdeGreedy} de poder.</b>` : '');
    };
    document.getElementById('sugerirMochila').onclick = () => { marcar(mercado.mochila.elegidos.map(u => u.id)); comparar(); };
    document.getElementById('sugerirGreedy').onclick = () => { marcar(mercado.greedy.elegidos.map(u => u.id)); comparar(); };
    document.getElementById('comprar').onclick = async () => {
        const destino = document.getElementById('destinoCompra').value;
        const compras = casillas().filter(c => c.checked).map(c => ({ unidad: c.value, destino }));
        const v = await ejecutar(api('POST', base() + '/comprar', { compras }));
        if (v) actualizar(v);
    };
    document.getElementById('repartir').onclick = async () => {
        const r = await ejecutar(api('GET', base() + '/reparto'));
        if (!r) return;
        const rp = r.reparto;
        document.getElementById('comparaCompra').innerHTML = `
            <b>Branch &amp; Bound</b>: maximizar el margen del frente más débil.<br>
            ${Object.entries(rp.porFrente).map(([f, us]) =>
                `${esc(porId[f].nombre)}: ${us.map(u => u.nombre).join(' + ') || '—'} → margen ${rp.margenFinal[f]}`).join('<br>')}<br>
            Margen mínimo <b>${rp.margenMinimo}</b> (greedy inicial ${rp.solucionInicial}, cota de la raíz ${rp.cotaRaiz})<br>
            Nodos: ${rp.nodosExplorados} explorados, ${rp.nodosPodados} podados por cota, ${rp.podadosPorSimetria} por simetría.
            <div class="fila"><button id="comprarReparto">Comprar con este reparto</button></div>`;
        document.getElementById('comprarReparto').onclick = async () => {
            const v = await ejecutar(api('POST', base() + '/comprar', { compras: r.comoCompra }));
            if (v) actualizar(v);
        };
    };
}

function dibujarIa() {
    const r = partida.ultimaJugadaIa;
    document.getElementById('ia').innerHTML = r ? `
        <h2>Última jugada de la IA</h2>
        <b>${esc(r.tecnica)}</b> · profundidad ${r.profundidad} · ${r.jugadasEnLaRaiz} jugadas en la raíz<br>
        ${r.nodosVisitados} nodos visitados · ${r.cortes} podas · ${r.milisegundos} ms<br>
        Plan: ${esc(r.plan.descripcion)}` : '<h2>IA</h2><span class="tenue">Todavía no jugó.</span>';
}

function dibujarLog() {
    const log = document.getElementById('log');
    log.innerHTML = partida.eventos.map(e => {
        const clase = e.startsWith('ROJO') ? 'ROJO' : e.startsWith('AZUL') ? 'AZUL' : e.startsWith('—') ? 'cobro' : '';
        return `<li class="${clase}">${esc(e)}</li>`;
    }).join('');
    log.scrollTop = log.scrollHeight;
}

document.getElementById('terminar').addEventListener('click', async () => {
    const boton = document.getElementById('terminar');
    boton.disabled = true;
    boton.textContent = 'La IA está pensando…';
    const v = await ejecutar(api('POST', base() + '/terminar'));
    boton.textContent = 'Terminar turno ▸';
    if (v) actualizar(v); else boton.disabled = false;
});

// ------------------------------------------------------------------ herramientas (un algoritmo cada una)

const HERRAMIENTAS = {
    async sugerencias() {
        const lista = await api('GET', base() + '/sugerencias');
        resaltados = new Set(lista.slice(0, 1).map(s => s.destino));
        dibujarMapa();
        if (!lista.length) return '<b>Greedy</b>: ningún ataque gana con las tropas actuales. Comprá o marchá primero.';
        return `<b>Greedy ganancia / riesgo</b> (miope: no mira la respuesta del rival)
            <table><tr><th>Desde</th><th>Hacia</th><th>Tropas</th><th>Def.</th><th>Ratio</th></tr>
            ${lista.map(s => `<tr class="clicable" data-o="${s.origen}" data-d="${s.destino}">
                <td>${s.origen}</td><td>${esc(s.nombreDestino)}</td><td>${s.tropasDisponibles}</td>
                <td>${s.defensa}</td><td>${s.ratio.toFixed(2)}</td></tr>`).join('')}</table>`;
    },
    async alcance() {
        insignias = await api('GET', base() + '/alcance?turnos=2');
        for (const id of Object.keys(insignias)) if (insignias[id] === 0) delete insignias[id];
        dibujarMapa();
        return `<b>BFS por niveles</b> desde tus territorios: el número negro es la cantidad de conquistas
            (turnos) para llegar. ${Object.entries(insignias).map(([id, n]) => `${id}: ${n}`).join(' · ')}`;
    },
    async objetivos() {
        const o = await api('GET', base() + '/objetivos?k=3');
        resaltados = new Set(o.masValiosos.map(x => x.id));
        dibujarMapa();
        return `<b>AVL</b> con ${o.territoriosEnElArbol} territorios ajenos por defensa
            (altura ${o.alturaDelArbol}, ${o.rotaciones} rotaciones).<br>
            Consulta por rango defensa ∈ [${o.defensaMinima}, ${o.defensaMaxima}]: ${o.enRango.map(x => x.id).join(', ') || 'ninguno'}<br>
            Los ${o.k} más valiosos: <b>${o.masValiosos.map(x => `${x.nombre} (def ${x.defensa}, +${x.produccion})`).join(', ') || '—'}</b>`;
    },
    async imperio() {
        const i = await api('GET', base() + '/imperio?jugador=AZUL');
        return `<b>DFS</b>: ${i.componentes.length} componente(s) ${i.partido ? '<span class="error">¡imperio partido!</span>' : '(entero)'}<br>
            ${i.componentes.map(c => '[' + c.join(', ') + ']').join(' ')}<br>
            <b>Prim</b> desde la capital: ${i.prim.aristas.map(a => `${a.origen}-${a.destino} (${a.costo})`).join(', ') || '—'} = ${i.prim.costoTotal}<br>
            <b>Kruskal</b>: ${i.kruskal.aristas.map(a => `${a.origen}-${a.destino} (${a.costo})`).join(', ') || '—'} = ${i.kruskal.costoTotal}<br>
            Mismo costo: ${i.mismoCosto ? 'sí' : 'no'} · mantenimiento ${i.mantenimiento} de oro por turno`;
    },
    async invasion() {
        const v = await api('GET', base() + '/invasion?costoMaximo=22&saltosMaximos=6');
        camino = v.masBarataPorDijkstra.vertices;
        dibujarMapa();
        return `<b>Backtracking</b>: rutas simples ${v.origen} → ${v.destino} con ≤ 22 días y ≤ 6 saltos
            (clic en una para verla). La dorada es la de Dijkstra (${v.masBarataPorDijkstra.costoTotal} días).
            <table>${v.rutas.map(r => `<tr class="clicable" data-ruta="${r.vertices.join(',')}">
                <td>${r.vertices.join(' → ')}</td><td>${r.costoTotal}</td></tr>`).join('')}</table>
            Nodos explorados: <b>${v.poda.exploradosConPoda}</b> con poda contra ${v.poda.exploradosSinPoda} sin poda
            (${v.poda.porcentajeAhorrado}% menos), mismas ${v.poda.rutasEncontradas} rutas.`;
    },
    async ranking() {
        const [valor, amenaza] = await Promise.all([
            api('GET', base() + '/ranking?criterio=valor'), api('GET', base() + '/ranking?criterio=amenaza')]);
        return `<b>QuickSort</b> por valor estratégico:
            ${valor.slice(0, 6).map(r => `${r.id} (${r.puntaje})`).join(', ')}…<br>
            <b>MergeSort</b> (estable) de tus territorios por amenaza − defensa:
            ${amenaza.map(r => `${r.id} (${r.puntaje})`).join(', ')}`;
    },
    async consejo() {
        const c = await api('GET', base() + '/consejo');
        return `<b>Alfa-beta</b> (profundidad ${c.profundidad}) jugando por vos: <b>${esc(c.plan.descripcion)}</b><br>
            compra: ${c.plan.compras.map(x => x.unidad + '@' + x.destino).join(', ') || 'nada'} ·
            ${c.nodosVisitados} nodos, ${c.cortes} podas, valor ${c.valor.toFixed(1)}`;
    },
    async comparacion() {
        const c = await api('GET', base() + '/ia/comparacion?profundidad=3');
        return `Mismo estado, profundidad ${c.profundidad}, ${c.jugadasEnLaRaiz} jugadas en la raíz:
            <table><tr><th></th><th>Nodos</th><th>ms</th><th>Valor</th></tr>
            <tr><td>MIN-MAX</td><td>${c.nodosMinimax}</td><td>${c.msMinimax}</td><td>${c.valorMinimax.toFixed(2)}</td></tr>
            <tr><td>Alfa-beta</td><td>${c.nodosAlfaBeta}</td><td>${c.msAlfaBeta}</td><td>${c.valorAlfaBeta.toFixed(2)}</td></tr></table>
            ${c.mismoValor ? 'Mismo valor' : 'DISTINTO valor (bug)'} · ${c.cortesAlfaBeta} podas ·
            <b>${c.porcentajeAhorrado}%</b> menos nodos. Jugada: ${esc(c.jugadaAlfaBeta.descripcion)}`;
    },
    async replay() {
        const r = await api('GET', base() + '/replay');
        const codigos = Object.entries(r.codigos).sort((a, b) => a[1].length - b[1].length);
        return `<b>Huffman</b> sobre el replay (${r.caracteres} caracteres, ${r.simbolosDistintos} símbolos):<br>
            <code>${esc(r.texto.length > 160 ? r.texto.slice(0, 160) + '…' : r.texto)}</code><br>
            ${r.bitsOriginales} bits → <b>${r.bitsComprimidos}</b> bits (${r.ahorroPorcentaje}% menos) ·
            decodifica igual: ${r.decodificaIgual ? 'sí' : 'no'}<br>
            Códigos: ${codigos.slice(0, 12).map(([s, c]) => `<code>${esc(s)}</code>=${c}`).join(' ')}…`;
    },
};

for (const boton of document.querySelectorAll('[data-h]')) {
    boton.addEventListener('click', async () => {
        if (!partida) return;
        const caja = document.getElementById('resultado');
        caja.classList.remove('tenue');
        caja.innerHTML = 'Calculando…';
        insignias = null;
        resaltados = new Set();
        camino = [];
        try {
            caja.innerHTML = await HERRAMIENTAS[boton.dataset.h]();
        } catch (e) {
            caja.innerHTML = `<span class="error">${esc(e.message)}</span>`;
            dibujarMapa();
        }
    });
}

document.getElementById('resultado').addEventListener('click', ev => {
    const fila = ev.target.closest('tr');
    if (!fila) return;
    if (fila.dataset.ruta) {
        camino = fila.dataset.ruta.split(',');
        dibujarMapa();
    } else if (fila.dataset.o && miTurno()) {
        seleccion = fila.dataset.o;
        prepararAtaque(fila.dataset.o, fila.dataset.d);
    }
});
