import React from 'react';
import { createRoot } from 'react-dom/client';
import Particles from './Particles';
createRoot(document.getElementById('particles-root')).render(<Particles particleColors={['#b8c6cc','#e5edef','#687c87']} particleCount={160} particleSpread={10} speed={0.12} particleBaseSize={65} glow={0.4} twinkle={0.25} moveParticlesOnHover hoverEffect="parallax" clickBurst={false} pixelRatio={1} />);
