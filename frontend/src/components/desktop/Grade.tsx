import React from 'react';
import { View } from 'react-native';
import { useLarguraAmpla } from '../../utils/useDesktop';

// No computador, mostra os itens em colunas; no celular, mantém a lista normal.
export default function Grade({ children, colunas = 2 }: { children: React.ReactNode; colunas?: number }) {
  // Janela de PC estreita: uma coluna só, para os cartões não ficarem espremidos
  const ampla = useLarguraAmpla();
  if (!ampla) return <>{children}</>;
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', marginHorizontal: -8 }}>
      {React.Children.map(children, (filho) =>
        filho ? <View style={{ width: `${100 / colunas}%`, paddingHorizontal: 8 }}>{filho}</View> : null,
      )}
    </View>
  );
}
