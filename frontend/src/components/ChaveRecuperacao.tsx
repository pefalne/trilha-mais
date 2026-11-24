import React, { useState } from 'react';
import { Linking, Platform, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

declare const navigator: { clipboard?: { writeText: (t: string) => Promise<void> } };
declare const document: any;
declare const URL: any;
declare const Blob: any;

export const SITE = 'https://trilha-mais.vercel.app';

// Abre /termos ou /privacidade: no navegador em nova aba, no celular pelo navegador do aparelho.
export function abrirPaginaDoSite(caminho: string) {
  if (Platform.OS === 'web' && typeof window !== 'undefined') {
    (window as any).open(caminho, '_blank', 'noopener');
    return;
  }
  Linking.openURL(SITE + caminho);
}

// Salva um texto como arquivo (só no navegador).
export function baixarArquivo(nome: string, conteudo: string, tipo = 'text/plain') {
  if (Platform.OS !== 'web') return false;
  const blob = new Blob([conteudo], { type: `${tipo};charset=utf-8` });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nome;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  return true;
}

type Props = {
  chave: string;
  email?: string;
  titulo?: string;
  textoBotao?: string;
  onConcluir: () => void;
};

// Mostra a chave de recuperação UMA vez, com copiar e baixar.
// Sem e-mail no projeto, é com ela (e o e-mail) que a pessoa troca a senha se esquecer.
export default function ChaveRecuperacao({ chave, email, titulo, textoBotao, onConcluir }: Props) {
  const [aviso, setAviso] = useState('');
  const [guardou, setGuardou] = useState(false);

  const copiar = async () => {
    try {
      await navigator.clipboard?.writeText(chave);
      setAviso('Chave copiada.');
      setGuardou(true);
    } catch {
      setAviso('Não deu para copiar. Anote a chave à mão.');
    }
  };

  const baixar = () => {
    const ok = baixarArquivo(
      'trilha-mais-chave-de-recuperacao.txt',
      `Trilha+ — chave de recuperação\n\nE-mail: ${email ?? ''}\nChave: ${chave}\n\n` +
        `Use esta chave em "Esqueci minha senha" para criar uma senha nova.\n` +
        `Guarde em lugar seguro e não compartilhe. Cada troca de senha gera uma chave nova.\n${SITE}/app/recuperar-senha\n`,
    );
    setAviso(ok ? 'Arquivo baixado.' : 'Anote a chave em lugar seguro.');
    setGuardou(true);
  };

  return (
    <View style={st.caixa}>
      <Text style={st.icone}>🔑</Text>
      <Text style={st.titulo}>{titulo ?? 'Guarde sua chave de recuperação'}</Text>
      <Text style={st.texto}>
        Se esquecer a senha, você vai precisar do seu e-mail e desta chave para criar uma nova.
        Ela aparece só agora.
      </Text>
      <View style={st.chaveBox}>
        <Text style={st.chave} selectable>
          {chave}
        </Text>
      </View>
      <View style={st.linha}>
        <TouchableOpacity style={st.secundario} onPress={copiar} activeOpacity={0.8}>
          <Text style={st.secundarioTexto}>Copiar</Text>
        </TouchableOpacity>
        {Platform.OS === 'web' ? (
          <TouchableOpacity style={st.secundario} onPress={baixar} activeOpacity={0.8}>
            <Text style={st.secundarioTexto}>Baixar .txt</Text>
          </TouchableOpacity>
        ) : null}
      </View>
      {aviso ? <Text style={st.aviso}>{aviso}</Text> : null}
      <TouchableOpacity
        style={st.marcar}
        onPress={() => setGuardou((v) => !v)}
        activeOpacity={0.8}
        accessibilityRole="checkbox"
        accessibilityState={{ checked: guardou }}
      >
        <View style={[st.check, guardou && st.checkOn]}>{guardou ? <Text style={st.checkV}>✓</Text> : null}</View>
        <Text style={st.marcarTexto}>Guardei minha chave em lugar seguro</Text>
      </TouchableOpacity>
      <TouchableOpacity
        style={[st.botao, !guardou && { opacity: 0.5 }]}
        disabled={!guardou}
        onPress={onConcluir}
        activeOpacity={0.85}
      >
        <Text style={st.botaoTexto}>{textoBotao ?? 'Continuar'}</Text>
      </TouchableOpacity>
    </View>
  );
}

const st = StyleSheet.create({
  caixa: { backgroundColor: '#FFFFFF', borderRadius: 20, padding: 24, borderWidth: 1, borderColor: '#E2E8F0' },
  icone: { fontSize: 36, textAlign: 'center', marginBottom: 8 },
  titulo: { fontSize: 20, fontWeight: '800', color: '#0B1B3F', textAlign: 'center', marginBottom: 8 },
  texto: { fontSize: 14, color: '#4A5568', textAlign: 'center', lineHeight: 20, marginBottom: 16 },
  chaveBox: { backgroundColor: '#FFF4EB', borderRadius: 14, borderWidth: 2, borderColor: '#F27A1A', borderStyle: 'dashed', paddingVertical: 16, paddingHorizontal: 12, marginBottom: 14 },
  chave: { fontSize: 22, fontWeight: '800', color: '#0B1B3F', textAlign: 'center', letterSpacing: 1.5, fontFamily: Platform.OS === 'web' ? 'ui-monospace, Menlo, monospace' : undefined },
  linha: { flexDirection: 'row', gap: 10, justifyContent: 'center' },
  secundario: { flex: 1, borderWidth: 1, borderColor: '#2563EB', borderRadius: 12, paddingVertical: 12, alignItems: 'center' },
  secundarioTexto: { color: '#2563EB', fontWeight: '700', fontSize: 15 },
  aviso: { textAlign: 'center', color: '#16A34A', fontWeight: '600', marginTop: 10 },
  marcar: { flexDirection: 'row', alignItems: 'center', marginTop: 18, marginBottom: 14 },
  check: { width: 22, height: 22, borderRadius: 6, borderWidth: 2, borderColor: '#94A3B8', alignItems: 'center', justifyContent: 'center', marginRight: 10 },
  checkOn: { backgroundColor: '#2563EB', borderColor: '#2563EB' },
  checkV: { color: '#FFFFFF', fontWeight: '900', fontSize: 14 },
  marcarTexto: { flex: 1, fontSize: 14, color: '#2D3748' },
  botao: { backgroundColor: '#EA580C', borderRadius: 12, padding: 16, alignItems: 'center' },
  botaoTexto: { color: '#FFFFFF', fontSize: 16, fontWeight: 'bold' },
});
