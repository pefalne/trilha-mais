import React from 'react';
import { Text, View, StyleSheet, TextStyle, StyleProp } from 'react-native';

// Mostra a resposta da IA com a formatação básica de Markdown
// (negrito, itálico, títulos, listas e tabelas) em vez dos símbolos crus.

type Props = { texto: string; estilo?: StyleProp<TextStyle> };

function trechos(linha: string, estilo?: StyleProp<TextStyle>) {
  // **negrito** e `código`
  const partes = linha.split(/(\*\*[^*]+\*\*|\*[^*\s][^*]*\*|`[^`]+`)/g).filter(Boolean);
  return partes.map((p, i) => {
    if (p.startsWith('**') && p.endsWith('**')) {
      return <Text key={i} style={[estilo, st.negrito]}>{p.slice(2, -2)}</Text>;
    }
    if (p.length > 2 && p.startsWith('*') && p.endsWith('*')) {
      return <Text key={i} style={[estilo, st.italico]}>{p.slice(1, -1)}</Text>;
    }
    if (p.startsWith('`') && p.endsWith('`')) {
      return <Text key={i} style={[estilo, st.codigo]}>{p.slice(1, -1)}</Text>;
    }
    return <Text key={i} style={estilo}>{p}</Text>;
  });
}

export default function TextoFormatado({ texto, estilo }: Props) {
  const linhas = texto.replace(/\r/g, '').replace(/<br\s*\/?>/gi, '\n').split('\n');
  const blocos: React.ReactNode[] = [];
  let codigo: string[] | null = null; // dentro de um bloco ```...```
  let linguagem = '';

  const fecharCodigo = (chave: number) => {
    if (!codigo) return;
    blocos.push(
      <View key={`c${chave}`} style={st.blocoCodigo}>
        {linguagem ? <Text style={st.rotuloCodigo}>{linguagem}</Text> : null}
        <Text style={st.textoCodigo} selectable>{codigo.join('\n').replace(/\s+$/, '')}</Text>
      </View>,
    );
    codigo = null;
    linguagem = '';
  };

  linhas.forEach((bruta, i) => {
    const cerca = bruta.trim().match(/^(```|~~~)\s*([\w+#.-]*)/);
    if (cerca) {
      if (codigo) fecharCodigo(i);
      else { codigo = []; linguagem = cerca[2] ?? ''; }
      return;
    }
    if (codigo) { codigo.push(bruta.replace(/\t/g, '    ')); return; }

    const linha = bruta.trim();
    if (!linha) {
      blocos.push(<View key={i} style={st.espaco} />);
      return;
    }
    if (/^(-{3,}|\*{3,}|_{3,})$/.test(linha)) return; // separador
    if (/^\|?\s*:?-{2,}/.test(linha) && /^[|\s:-]+$/.test(linha)) return; // linha de alinhamento da tabela

    const titulo = linha.match(/^#{1,6}\s+(.*)$/);
    if (titulo) {
      blocos.push(<Text key={i} style={[estilo, st.titulo]}>{trechos(titulo[1].replace(/\*\*/g, ''), [estilo, st.titulo])}</Text>);
      return;
    }

    if (linha.startsWith('|')) {
      const celulas = linha.split('|').map((x) => x.trim()).filter(Boolean);
      blocos.push(
        <View key={i} style={st.linhaLista}>
          <Text style={estilo}>• </Text>
          <Text style={[estilo, st.flex]}>{trechos(celulas.join(' — '), estilo)}</Text>
        </View>,
      );
      return;
    }

    const item = linha.match(/^([-*•]|\d+[.)])\s+(.*)$/);
    if (item) {
      const marcador = /^\d/.test(item[1]) ? `${item[1].replace(')', '.')} ` : '• ';
      blocos.push(
        <View key={i} style={st.linhaLista}>
          <Text style={estilo}>{marcador}</Text>
          <Text style={[estilo, st.flex]}>{trechos(item[2], estilo)}</Text>
        </View>,
      );
      return;
    }

    blocos.push(<Text key={i} style={estilo}>{trechos(linha, estilo)}</Text>);
  });

  fecharCodigo(linhas.length); // resposta cortada no meio do bloco
  return <View>{blocos}</View>;
}

const st = StyleSheet.create({
  negrito: { fontWeight: '700' },
  italico: { fontStyle: 'italic' },
  codigo: { fontFamily: 'monospace', backgroundColor: '#EEF2F7', borderRadius: 4, paddingHorizontal: 3 },
  blocoCodigo: { backgroundColor: '#0F172A', borderRadius: 10, padding: 12, marginVertical: 6 },
  rotuloCodigo: { color: '#94A3B8', fontSize: 11, fontWeight: '700', marginBottom: 6, textTransform: 'uppercase' },
  textoCodigo: { color: '#E2E8F0', fontFamily: 'monospace', fontSize: 13, lineHeight: 19 },
  titulo: { fontWeight: '700', fontSize: 16, marginTop: 4, marginBottom: 2 },
  espaco: { height: 8 },
  linhaLista: { flexDirection: 'row', marginBottom: 2 },
  flex: { flex: 1 },
});
