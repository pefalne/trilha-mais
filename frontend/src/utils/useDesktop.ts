import { Platform, useWindowDimensions } from 'react-native';

// Layout de computador na versão web:
// - qualquer tela com pelo menos 1024 px; ou
// - computador de verdade (mouse/touchpad) com a janela diminuída até 640 px.
// Assim, no PC, diminuir a janela não joga o app para o layout de celular.
// No celular (app ou navegador) continua tudo como antes.
export const LARGURA_DESKTOP = 1024;
export const LARGURA_MINIMA_PC = 640;

const ehComputador = () =>
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(hover: hover) and (pointer: fine)').matches;

export default function useDesktop() {
  const { width } = useWindowDimensions();
  if (Platform.OS !== 'web') return false;
  return width >= LARGURA_DESKTOP || (ehComputador() && width >= LARGURA_MINIMA_PC);
}

// Janela larga o bastante para duas colunas e para o painel da marca no login.
export function useLarguraAmpla() {
  const { width } = useWindowDimensions();
  return useDesktop() && width >= LARGURA_DESKTOP;
}
