import React, { useRef } from 'react';
import { motion, useScroll, useTransform } from 'framer-motion';
import './jornada.css';

const mensagem = 'Meu sistema parou de funcionar.';
const etapas = [
  {
    titulo: 'O cliente chama pelo Telegram.',
    texto: 'A conversa começa no canal que o cliente já usa.',
    exemplo: mensagem,
    autor: 'Cliente',
  },
  {
    titulo: 'A IA encaminha a conversa.',
    texto: 'O chatbot acolhe o cliente e encaminha o caso com o histórico para a fila.',
    exemplo: 'Entendi. Vou encaminhar seu caso para a equipe de suporte.',
    autor: 'Chatbot NEXUS',
  },
  {
    titulo: 'O atendente já tem o contexto.',
    texto: 'Ao assumir no NEXUS, a equipe lê o que já aconteceu e responde pelo painel.',
    exemplo: 'Olá! Vi o que aconteceu. Vou te ajudar a verificar o sistema.',
    autor: 'Atendente',
  },
];

export default function JornadaAtendimento() {
  const section = useRef(null);
  // Numeric offsets keep Motion's progress aligned with the sticky section in Chrome.
  const { scrollYProgress } = useScroll({ target: section, offset: ['0 0', '1 1'] });
  const primeiraEtapa = useTransform(scrollYProgress, [0.18, 0.22], [1, 0]);
  const segundaEtapa = useTransform(scrollYProgress, [0.22, 0.28, 0.68, 0.72], [0, 1, 1, 0]);
  const terceiraEtapa = useTransform(scrollYProgress, [0.72, 0.78], [0, 1]);
  const respostaIa = useTransform(scrollYProgress, [0.2, 0.35], [0, 1]);
  const entradaIa = useTransform(respostaIa, [0, 1], [10, 0]);
  const telegram = useTransform(scrollYProgress, [0.46, 0.54], [1, 0]);
  const painel = useTransform(scrollYProgress, [0.54, 0.62], [0, 1]);
  const fila = useTransform(scrollYProgress, [0.54, 0.62, 0.68, 0.72], [0, 1, 1, 0]);
  const statusAtendente = useTransform(scrollYProgress, [0.72, 0.78], [0, 1]);
  const atendente = useTransform(scrollYProgress, [0.69, 0.83], [0, 1]);
  const entradaAtendente = useTransform(atendente, [0, 1], [10, 0]);
  const opacidades = [primeiraEtapa, segundaEtapa, terceiraEtapa];

  return (
    <section ref={section} className="jornada-demo" id="jornada" aria-labelledby="jornada-title">
      <div className="jornada-palco">
        <div className="jornada-copy">
          <h2 id="jornada-title">Do Telegram <span>ao painel.</span></h2>
          <ol className="jornada-etapas">
            {etapas.map(({ titulo, texto, exemplo, autor }) => (
              <li key={autor}>
                <h3>{titulo}</h3>
                <p>{texto}</p>
                <div className="jornada-balao">
                  <span>{autor}</span>
                  <p>{exemplo}</p>
                </div>
              </li>
            ))}
          </ol>
          <div className="jornada-legendas" aria-hidden="true">
            {etapas.map(({ titulo, texto }, index) => (
              <motion.div className="jornada-legenda" key={titulo} style={{ opacity: opacidades[index] }}>
                <p className="jornada-legenda-titulo">{titulo}</p>
                <p>{texto}</p>
              </motion.div>
            ))}
          </div>
        </div>

        <div className="jornada-janela" aria-hidden="true">
          <div className="jornada-barra">
            <motion.div className="jornada-canal" style={{ opacity: telegram }}>
              <span className="jornada-avatar">
                <svg viewBox="0 0 24 24" width="17" height="17" fill="currentColor">
                  <path d="m20.7 4.1-3.1 15.1c-.1.7-.6.9-1.2.5l-4.7-3.5-2.2 2.2c-.3.2-.4.3-.8.3l.3-4.7 8.5-7.7c.4-.3 0-.5-.5-.2l-10.5 6.6-4.5-1.4c-1-.3-1-1 .2-1.5l17.6-6.8c.8-.3 1.2.2.9 1.1Z" />
                </svg>
              </span>
              <div><strong>Telegram</strong><span>NEXUS Atendimento</span></div>
            </motion.div>
            <motion.div className="jornada-canal" style={{ opacity: painel }}>
              <span className="jornada-avatar jornada-avatar-painel">N</span>
              <div><strong>NEXUS</strong><span>Painel de atendimento</span></div>
            </motion.div>
            <div className="jornada-status">
              <motion.span style={{ opacity: telegram }}>Conversa</motion.span>
              <motion.span style={{ opacity: fila }}>Na fila de atendimento</motion.span>
              <motion.span className="jornada-status-ativo" style={{ opacity: statusAtendente }}>Em atendimento</motion.span>
            </div>
          </div>
          <div className="jornada-chat">
            <div className="jornada-balao jornada-cliente">
              <span>{etapas[0].autor}</span>
              <p>{mensagem}</p>
            </div>
            <motion.div className="jornada-balao jornada-ia" style={{ opacity: respostaIa, y: entradaIa }}>
              <span>{etapas[1].autor}</span>
              <p>{etapas[1].exemplo}</p>
            </motion.div>
            <motion.div className="jornada-balao jornada-humano" style={{ opacity: atendente, y: entradaAtendente }}>
              <span>{etapas[2].autor}</span>
              <p>{etapas[2].exemplo}</p>
            </motion.div>
          </div>
        </div>
      </div>
    </section>
  );
}
