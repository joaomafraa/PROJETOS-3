import React, { useEffect, useRef } from 'react';
import { createRoot } from 'react-dom/client';
import { motion, useScroll, useSpring, useTransform } from 'framer-motion';
import './style.css';
import GradientASCII from './GradientASCII';
import Orb from './Orb';
import Magnet from './Magnet';

function Flow({ progress }) {
  const video = useRef(null);
  const opacity = useTransform(progress, [0, .58, .80], [1, 1, 0]);
  const escurecer = useTransform(progress, [0, .14, .32, .60, .80], [0, .35, .52, .54, .8]);
  const deslocamento = useSpring(useTransform(progress, [0, .25, .60, .80], ['0%', '10%', '-8%', '-12%']), {stiffness:85,damping:25});
  const altura = useSpring(useTransform(progress, [0, .25, .60, .80], ['0%', '-8%', '-2%', '-8%']), {stiffness:85,damping:25});
  const giro = useSpring(useTransform(progress, [0, .25, .60, .80], [0, -7, 14, 17]), {stiffness:85,damping:25});
  const profundidade = useSpring(useTransform(progress, [0, .25, .60, .80], [1, 1.18, 1.26, 1.3]), {stiffness:85,damping:25});
  useEffect(() => {
    const el = video.current; let raf, inicio;
    const tick = tempo => { inicio ??= tempo; const p = Math.min((tempo-inicio)/2200,1); el.playbackRate=1+1.2*Math.pow(1-p,3); if(p<1) raf=requestAnimationFrame(tick); };
    const start = () => { if(inicio===undefined && !raf) raf=requestAnimationFrame(tick); };
    el.addEventListener('playing',start); if(!el.paused) start();
    return () => {cancelAnimationFrame(raf);el.removeEventListener('playing',start);};
  }, []);
  return <motion.div className="flow-backdrop flow-main" style={{opacity}} aria-hidden="true"><motion.div className="flow-scroll" style={{x:deslocamento,y:altura,rotate:giro,scale:profundidade}}><motion.div className="flow-scene" initial={{scale:1.2,x:'-6%',opacity:0}} animate={{scale:1,x:'0%',opacity:1}} transition={{duration:1.7,ease:[.16,1,.3,1]}}><video ref={video} src="/abertura/media/wxn-animacao-1-loop.mp4" autoPlay muted loop playsInline preload="auto"/></motion.div></motion.div><div className="flow-ambient"/><motion.div className="flow-darken" style={{opacity:escurecer}}/><div className="flow-shade"/></motion.div>;
}

function App() {
  const jornada = useRef(null);
  const { scrollYProgress } = useScroll({ target: jornada, offset: ['start start', 'end end'] });
  const heroOpacity = useTransform(scrollYProgress, [0, .09, .17], [1, 1, 0]);
  const heroY = useTransform(scrollYProgress, [0, .17], [0, -50]);
  return <div className="page">
    <header className="mast"><a className="logo" href="/" aria-label="NEXUS início"><span>NEXUS</span></a><nav aria-label="Navegação principal"><Magnet padding={65} magnetStrength={4} maxOffset={10}><a className="nav-login" href="/login.html">Entrar <span aria-hidden="true">↗</span></a></Magnet></nav></header>
    <main>
      <div ref={jornada} className="journey">
        <div className="sticky-stage"><Flow progress={scrollYProgress}/></div>
        <section className="chapter hero" aria-labelledby="hero-title">
          <motion.div className="hero-copy" style={{opacity: heroOpacity, y: heroY}}><motion.div initial={{opacity:0,y:20}} animate={{opacity:1,y:0}} transition={{duration:.85,delay:.25,ease:[.23,1,.32,1]}}>
            <p className="eyebrow">NEXUS <span> / </span> INTELIGÊNCIA NO ATENDIMENTO</p>
            <h1 id="hero-title">Uma conversa começa.<br/><span>O atendimento continua.</span></h1>
            <div className="hero-bottom"><p>Inteligência artificial e pessoas conectadas para acompanhar cada conversa, do início à solução.</p></div>
          </motion.div></motion.div>
          <div className="scroll-note">Role para explorar <span>↓</span></div>
        </section>
        <section className="chapter chapter-middle" id="jornada" aria-labelledby="chapter-title"><motion.div className="chapter-copy" initial={{opacity:0,y:36}} whileInView={{opacity:1,y:0}} viewport={{amount:.45,once:false}} transition={{duration:.65}}><p className="eyebrow">CONVERSAS COM CONTEXTO</p><h2 id="chapter-title">Tecnologia que escuta.<br/><span>Pessoas que continuam.</span></h2><p>O chatbot inicia a conversa. Quando a situação pede um atendente, o histórico acompanha o próximo passo.</p></motion.div></section>
        <section className="chapter chapter-final" aria-labelledby="final-title"><motion.div className="chapter-copy" initial="hidden" whileInView="visible" viewport={{amount:.35,once:false}} variants={{hidden:{},visible:{transition:{staggerChildren:.12}}}}><motion.p className="eyebrow" variants={{hidden:{opacity:0,y:22},visible:{opacity:1,y:0,transition:{duration:.6,ease:[.23,1,.32,1]}}}}>DO INÍCIO À CONTINUIDADE</motion.p><h2 id="final-title"><motion.span className="copy-line copy-line-primary" variants={{hidden:{opacity:0,x:35,y:22},visible:{opacity:1,x:0,y:0,transition:{duration:.8,ease:[.23,1,.32,1]}}}}>Cada mensagem</motion.span><motion.span className="copy-line" variants={{hidden:{opacity:0,x:55,y:30},visible:{opacity:1,x:0,y:0,transition:{duration:.8,ease:[.23,1,.32,1]}}}}>faz parte da história.</motion.span></h2><motion.p variants={{hidden:{opacity:0,y:18},visible:{opacity:1,y:0,transition:{duration:.7,ease:[.23,1,.32,1]}}}}>Fila, conversa e informações disponíveis em um espaço para a equipe acompanhar o atendimento.</motion.p></motion.div></section>
      <section className="product" aria-labelledby="product-title"><div className="ascii-background" aria-hidden="true"><GradientASCII colors={["#15191c", "#353e43", "#8a989f", "#d4dedf"]} shape="horizon" cellSize={18} density={0.8} glyphOpacity={0.85} gradientOpacity={0.35} glow={0.12} speed={0.6} mouseInteraction={false} clickEffect="none" dpr={1}/></div><div className="section-label">O PAINEL NEXUS</div><div className="product-copy"><h2 id="product-title">Uma visão clara.<br/><span>Para seguir em frente.</span></h2><p>Acompanhe a fila de conversas, consulte o histórico e tenha o contexto disponível para continuar o atendimento.</p></div></section>
      <section className="closing"><div className="orb-art" aria-hidden="true"><Orb colors={["#d7e0e3", "#8da0aa", "#1b2b34"]} backgroundColor="#050505" hoverIntensity={0.8} trail={0.7} rotateOnHover followCursor clickPulse speed={0.7} dpr={1} /></div><motion.div className="closing-copy" initial={{opacity:0,y:30}} whileInView={{opacity:1,y:0}} viewport={{once:true,amount:.3}} transition={{type:"spring",duration:.8,bounce:0}}><p className="eyebrow">NEXUS · ATENDIMENTO INTELIGENTE</p><h2>Mais contexto.<br/><span>Melhores conversas.</span></h2><Magnet padding={65} magnetStrength={4} maxOffset={10} wrapperClassName="magnet-cta"><a className="cta" href="/login.html">Acessar painel <span aria-hidden="true">↗</span></a></Magnet></motion.div></section>
      </div>
    </main><footer><span>NEXUS · Atendimento inteligente</span><a href="/login.html">Entrar no painel ↗</a></footer>
  </div>;
}
createRoot(document.getElementById('root')).render(<App/>);
