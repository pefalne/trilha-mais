import { Platform, useWindowDimensions } from 'react-native';

// Tela de computador: versão web com pelo menos 1024 px de largura.
// No celular (app ou navegador) continua tudo como antes.
export const LARGURA_DESKTOP = 1024;

export default function useDesktop() {
  const { width } = useWindowDimensions();
  return Platform.OS === 'web' && width >= LARGURA_DESKTOP;
}
