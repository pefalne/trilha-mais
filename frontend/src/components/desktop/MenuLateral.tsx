import React from 'react';
import { Image, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { colors } from '../../theme/tokens';

// Menu fixo à esquerda na versão de computador (substitui a barra de baixo do celular).

type Item = { rota: string; rotulo: string; icone: string; grupo: string[] };

const itens: Item[] = [
  { rota: 'Home', rotulo: 'Início', icone: '🏠', grupo: ['Home'] },
  { rota: 'Agenda', rotulo: 'Agenda', icone: '🗓', grupo: ['Agenda'] },
  { rota: 'Materias', rotulo: 'Matérias', icone: '📖', grupo: ['Materias', 'Materia', 'Assuntos', 'Quiz', 'Resultado'] },
  { rota: 'Progresso', rotulo: 'Progresso', icone: '📊', grupo: ['Progresso'] },
  { rota: 'Perfil', rotulo: 'Perfil', icone: '👤', grupo: ['Perfil'] },
];

const rotasIA = ['ChatIAScreen', 'ChatIA', 'Chat'];

type Props = { rotaAtual: string | null; aoIr: (rota: string) => void };

export default function MenuLateral({ rotaAtual, aoIr }: Props) {
  const iaAtiva = !!rotaAtual && rotasIA.includes(rotaAtual);
  return (
    <View style={st.menu}>
      <View style={st.marca}>
        <Image source={require('../../imagens/Logo.jpeg')} style={st.logo} resizeMode="contain" />
        <Text style={st.nome}>
          Trilha<Text style={st.mais}>+</Text>
        </Text>
      </View>

      <View style={st.lista}>
        {itens.map((item) => {
          const ativo = !!rotaAtual && item.grupo.includes(rotaAtual);
          return (
            <TouchableOpacity
              key={item.rota}
              style={[st.item, ativo && st.itemAtivo]}
              onPress={() => aoIr(item.rota)}
              activeOpacity={0.75}
            >
              <Text style={st.icone}>{item.icone}</Text>
              <Text style={[st.rotulo, ativo && st.rotuloAtivo]}>{item.rotulo}</Text>
            </TouchableOpacity>
          );
        })}
      </View>

      <TouchableOpacity
        style={[st.ia, iaAtiva && st.iaAtiva]}
        onPress={() => aoIr('ChatIAScreen')}
        activeOpacity={0.85}
      >
        <Text style={st.iaTitulo}>💬  Assistente IA</Text>
        <Text style={st.iaTexto}>Tire dúvidas sobre qualquer matéria.</Text>
      </TouchableOpacity>
    </View>
  );
}

const st = StyleSheet.create({
  menu: {
    width: 260,
    backgroundColor: colors.background,
    borderRightWidth: 1,
    borderRightColor: colors.border,
    paddingHorizontal: 18,
    paddingTop: 28,
    paddingBottom: 24,
  },
  marca: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 8, marginBottom: 36 },
  logo: { width: 40, height: 43 },
  nome: { fontSize: 26, fontWeight: '800', color: colors.primaryDark, letterSpacing: -0.3 },
  mais: { color: colors.accent },
  lista: { gap: 4, flex: 1 },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingVertical: 12,
    paddingHorizontal: 14,
    borderRadius: 12,
  },
  itemAtivo: { backgroundColor: colors.progressBg },
  icone: { fontSize: 18, width: 24, textAlign: 'center' },
  rotulo: { fontSize: 16, color: colors.textSecondary, fontWeight: '500' },
  rotuloAtivo: { color: colors.primaryDark, fontWeight: '700' },
  ia: { backgroundColor: colors.primary, borderRadius: 16, padding: 18 },
  iaAtiva: { backgroundColor: colors.primaryDark },
  iaTitulo: { color: colors.textOnPrimary, fontSize: 16, fontWeight: '700' },
  iaTexto: { color: colors.textOnPrimary, opacity: 0.85, fontSize: 13, marginTop: 6, lineHeight: 18 },
});
