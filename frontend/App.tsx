import React, { useEffect } from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import AppRoutes from './src/Navigation/AppRoutes';
import useDesktop from './src/utils/useDesktop';
import { acordarServidor } from './src/services/api';

// O Trilha+ foi desenhado para celular. No navegador do celular ele fica numa coluna
// com largura de celular; no computador (>= 1024 px) usa o layout próprio de desktop.
export default function App() {
  const desktop = useDesktop();
  useEffect(() => {
    acordarServidor();
  }, []);
  if (Platform.OS !== 'web' || desktop) {
    return <AppRoutes />;
  }

  return (
    <View style={styles.fundoWeb}>
      <View style={styles.colunaApp}>
        <AppRoutes />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  fundoWeb: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: '#E8ECF4',
  },
  colunaApp: {
    flex: 1,
    width: '100%',
    maxWidth: 480,
    backgroundColor: '#FFFFFF',
    overflow: 'hidden',
  },
});
