import React, { useEffect, useRef } from 'react';
import { createRoot } from 'react-dom/client';
import { motion, useReducedMotion, useScroll, useSpring, useTransform } from 'framer-motion';
import './style.css';
import GradientASCII from './GradientASCII';
import JornadaAtendimento from './JornadaAtendimento';
import Magnet from './Magnet';
import Orb from './Orb';

const curvaEntrada = [.23, 1, .32, 1];

function Flow({ progress }) {
  const video = useRef(null);
  const opacity = useTransform(progress, [0, .35, .9], [1, 1, 0]);
  const deslocamento = useSpring(useTransform(progress, [0, .85], [0, -30]), { stiffness: 85, damping: 25 });
  const luz = useTransform(progress, [0, .24, .7], [.15, .65, 0]);
  const velocidadeInicial = 1.75;

  useEffect(() => {
    const el = video.current;
    let frame;
    let inicio;

    function desacelerar(tempo) {
      inicio ??= tempo;
      const progresso = Math.min((tempo - inicio) / 2200, 1);
      el.playbackRate = 1 + (velocidadeInicial - 1) * (1 - progresso) ** 3;
      if (progresso < 1) frame = requestAnimationFrame(desacelerar);
    }

    function acelerarEntrada() {
      frame = requestAnimationFrame(desacelerar);
    }

    el.playbackRate = velocidadeInicial;
    if (el.paused) el.addEventListener('playing', acelerarEntrada, { once: true });
    else acelerarEntrada();

    let visivel = false;
    function atualizarReproducao() {
      if (visivel && !document.hidden) void el.play().catch(() => {});
      else el.pause();
    }

    const observer = new IntersectionObserver(([entry]) => {
      visivel = entry.isIntersecting;
      atualizarReproducao();
    });
    observer.observe(el.closest('.hero'));
    document.addEventListener('visibilitychange', atualizarReproducao);

    return () => {
      document.removeEventListener('visibilitychange', atualizarReproducao);
      observer.disconnect();
      cancelAnimationFrame(frame);
      el.removeEventListener('playing', acelerarEntrada);
    };
  }, []);

  return (
    <div className="hero-art" aria-hidden="true">
      <motion.div className="flow-backdrop flow-main" style={{ opacity }}>
        <motion.div className="flow-scene" style={{ y: deslocamento }}>
          <video
            ref={video}
            src="/abertura/media/wxn-animacao-1-loop.mp4"
            autoPlay muted loop playsInline preload="auto"
          />
        </motion.div>
        <motion.div className="flow-ambient" style={{ opacity: luz }} />
        <div className="flow-shade" />
      </motion.div>
    </div>
  );
}

function App() {
  const movimentoReduzido = useReducedMotion();
  const hero = useRef(null);
  const { scrollYProgress } = useScroll({ target: hero, offset: ['start start', 'end start'] });

  return (
    <div className="page">
      <header className="mast">
        <a className="logo" href="/" aria-label="NEXUS início"><span>NEXUS</span></a>
        <nav aria-label="Navegação principal">
          <Magnet padding={65} magnetStrength={4} maxOffset={10}>
            <a className="nav-login" href="/entrar">Entrar <span aria-hidden="true">↗</span></a>
          </Magnet>
        </nav>
      </header>
      <main>
        <section ref={hero} className="chapter hero" aria-labelledby="hero-title">
          {!movimentoReduzido && <Flow progress={scrollYProgress} />}
          <motion.div
            className="hero-copy"
            initial={movimentoReduzido ? false : { opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: .55, ease: curvaEntrada }}
          >
            <p className="eyebrow">NEXUS <span> / </span> INTELIGÊNCIA NO ATENDIMENTO</p>
            <h1 id="hero-title">Uma conversa começa.<br /><span>O atendimento continua.</span></h1>
            <div className="hero-bottom">
              <p>Inteligência artificial e pessoas conectadas para acompanhar cada conversa, do início à solução.</p>
            </div>
          </motion.div>
          <a href="#jornada" className="scroll-note">Explore o atendimento <span aria-hidden="true">↓</span></a>
        </section>
        <JornadaAtendimento />
        <section className="product" aria-labelledby="product-title">
          {!movimentoReduzido && (
            <div className="ascii-background" aria-hidden="true">
              <GradientASCII colors={['#15191c', '#353e43', '#8a989f', '#d4dedf']} shape="horizon" cellSize={18} density={0.8} glyphOpacity={0.85} gradientOpacity={0.35} glow={0.12} speed={0.6} mouseInteraction={false} clickEffect="none" dpr={1} />
            </div>
          )}
          <div className="section-label">O PAINEL NEXUS</div>
          <div className="product-copy">
            <h2 id="product-title">Uma visão clara.<br /><span>Para seguir em frente.</span></h2>
            <p>Acompanhe a fila de conversas, consulte o histórico e tenha o contexto disponível para continuar o atendimento.</p>
          </div>
        </section>
        <section className="closing">
          {!movimentoReduzido && (
            <div className="orb-art" aria-hidden="true">
              <Orb colors={['#d7e0e3', '#8da0aa', '#1b2b34']} backgroundColor="#050505" hoverIntensity={0.8} trail={0.7} rotateOnHover followCursor clickPulse speed={0.7} dpr={1} />
            </div>
          )}
          <motion.div
            className="closing-copy"
            initial={movimentoReduzido ? false : { opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: .3 }}
            transition={{ type: 'spring', duration: .8, bounce: 0 }}
          >
            <p className="eyebrow">NEXUS · ATENDIMENTO INTELIGENTE</p>
            <h2>Mais contexto.<br /><span>Melhores conversas.</span></h2>
            <Magnet padding={65} magnetStrength={4} maxOffset={10} wrapperClassName="magnet-cta">
              <a className="cta" href="/entrar">Acessar painel <span aria-hidden="true">↗</span></a>
            </Magnet>
          </motion.div>
        </section>
      </main>
      <footer><span>NEXUS · Atendimento inteligente</span><a href="/entrar">Entrar no painel ↗</a></footer>
    </div>
  );
}

createRoot(document.getElementById('root')).render(<App />);
