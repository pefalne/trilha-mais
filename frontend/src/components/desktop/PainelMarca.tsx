import React from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { colors } from '../../theme/tokens';

// Lado esquerdo das telas de entrar/cadastrar na versão de computador.

const recursos = [
  'Agenda de estudos da semana',
  'Matérias e assuntos organizados',
  'Simulados para treinar',
  'Assistente de IA para tirar dúvidas',
];

export default function PainelMarca() {
  return (
    <View style={st.painel}>
      <View style={st.bola1} />
      <View style={st.bola2} />
      <View style={st.marca}>
        <View style={st.logoCaixa}>
          <Image source={require('../../imagens/Logo.jpeg')} style={st.logo} resizeMode="contain" />
        </View>
        <Text style={st.nome}>
          Trilha<Text style={st.mais}>+</Text>
        </Text>
      </View>

      <View>
        <Text style={st.titulo}>Seus estudos{'\n'}num só lugar.</Text>
        <View style={st.lista}>
          {recursos.map((r) => (
            <View key={r} style={st.linha}>
              <View style={st.ponto} />
              <Text style={st.recurso}>{r}</Text>
            </View>
          ))}
        </View>
      </View>

      <Text style={st.rodape}>Projeto do curso de ADS · Uninassau Caruaru</Text>
    </View>
  );
}

const st = StyleSheet.create({
  painel: {
    flex: 1,
    backgroundColor: colors.primary,
    paddingHorizontal: 64,
    paddingVertical: 56,
    justifyContent: 'space-between',
    overflow: 'hidden',
  },
  bola1: {
    position: 'absolute', width: 640, height: 640, borderRadius: 320,
    right: -220, top: -160, backgroundColor: 'rgba(255,255,255,0.07)',
  },
  bola2: {
    position: 'absolute', width: 420, height: 420, borderRadius: 210,
    left: -160, bottom: -180, backgroundColor: 'rgba(255,255,255,0.05)',
  },
  marca: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  logoCaixa: { backgroundColor: '#FFFFFF', borderRadius: 14, padding: 8 },
  logo: { width: 36, height: 38 },
  nome: { color: '#FFFFFF', fontSize: 32, fontWeight: '800', letterSpacing: -0.4 },
  mais: { color: colors.accent },
  titulo: { color: '#FFFFFF', fontSize: 56, lineHeight: 60, fontWeight: '800', letterSpacing: -0.8 },
  lista: { marginTop: 32, gap: 14 },
  linha: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  ponto: { width: 10, height: 10, borderRadius: 5, backgroundColor: colors.accent },
  recurso: { color: '#FFFFFF', fontSize: 19, opacity: 0.95 },
  rodape: { color: '#FFFFFF', opacity: 0.7, fontSize: 14 },
});
