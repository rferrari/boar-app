# Guia do BOAR

## O que é o BOAR
O BOAR é um assistente de pesquisa com IA que roda inteiro no seu celular. Ele responde a partir de uma biblioteca guardada no celular e lista embaixo da resposta as fontes que usou, para você conferir. Depois de configurado, funciona sem internet, e suas perguntas nunca saem do celular. BOAR é o nome do app; o javali é só o mascote.

## Funcionando offline
O BOAR usa a internet só para baixar o que precisa: na configuração inicial (o modelo de respostas, o modelo de busca e um pacote de conhecimento) e, depois, só se você quiser baixar outro modelo ou pacote, ou buscar modelos no Hugging Face. Fora isso, tudo roda no celular, e o modo avião não atrapalha. A etiqueta ONLINE / OFFLINE ao lado de "BOAR", no topo da conversa, mostra se o celular está conectado; o BOAR responde do mesmo jeito nos dois casos.

## Configurando o BOAR
A primeira abertura tem três passos: boas-vindas (escolha o idioma e toque em Começar), Escolha o modelo de respostas (um vem recomendado para a memória do seu celular) e Escolha o conhecimento (Enciclopédia ou Essencial). Depois toque em Baixar. Enquanto baixa, um guia curto mostra: Do seu jeito, Seus arquivos, Como o BOAR responde, Como perguntar, Sua primeira pergunta, o PIN, Notificações, Privacidade e memória, e Boar Labs. Toda página tem Agora não, e toda escolha pode ser mudada depois em Configurações. Se os downloads pararem, toque em Baixar de novo; se o celular ficou sem internet, toque em Tentar de novo quando ela voltar. Se você fechar o BOAR antes de terminar, a configuração recomeça do primeiro passo com suas escolhas guardadas, e o download continua de onde parou. Quando tudo chegar, toque em Abrir o BOAR. Para rodar a configuração de novo: Configurações → Recomeçar → Rodar a configuração de novo.

## Fazendo perguntas
Escreva a pergunta embaixo, na conversa, e toque em enviar. Pergunte em português ou em inglês. Perguntas claras e específicas funcionam melhor: diga do que você está falando ("Como tratar uma queimadura leve?", "Quando a Torre Eiffel foi construída?"). Uma conversa vazia sugere Temas de pesquisa para começar. Enquanto o BOAR trabalha, uma linha embaixo da pergunta diz o que ele está fazendo (buscando, raciocinando, escrevendo) e há quanto tempo. Toque em parar para encerrar a resposta antes; o que já foi escrito e as fontes encontradas até ali ficam. Comece uma conversa nova com o + no topo da conversa ou com Nova Sessão de Pesquisa no menu. Toque e segure uma mensagem sua para colocá-la de volta na caixa de texto.

## Estilos de resposta
O botão de estilo à esquerda de onde você escreve, ou Configurações → Como o BOAR responde, escolhe como o BOAR responde:
- Sucinto e Direto: respostas curtas.
- Completo e Detalhado: explicações completas. É o padrão.
- Arquivos pessoais: responde só a partir dos seus arquivos.
O estilo vale para as próximas perguntas até você mudar. Perguntas de saúde e sobre frase-semente sempre recebem resposta curta, com um aviso embaixo, e cumprimentos recebem uma resposta breve. Em Configurações → Como o BOAR responde também fica o Máximo de tokens de saída, o tamanho máximo de uma resposta; se uma resposta parar nesse limite, ela oferece permitir mais e refazer.

## Pesquisa Profunda
A Pesquisa Profunda (Deep Research) é para perguntas grandes. Ligue com o botão 🔬 no topo da conversa, na janela do botão de estilo ou em Configurações → Como o BOAR responde (Modo Pesquisa Profunda). O BOAR divide a pergunta em 2 ou 3 menores, pesquisa a biblioteca para cada uma e escreve uma resposta mais longa, com fontes. Com ela ligada, a caixa de texto mostra "Pergunta para o Deep Research…". É mais lenta que uma resposta normal: alguns minutos no modelo menor. Funciona com Sucinto e Direto e com Completo e Detalhado; com Arquivos pessoais, o BOAR responde normalmente.

## Arquivos pessoais
Arquivos pessoais deixa o BOAR responder a partir dos seus documentos: notas, PDFs e planilhas (.txt, .md, .csv, .json e .pdf com texto selecionável; PDFs escaneados ou protegidos por senha não podem ser lidos, e arquivos muito grandes são recusados). No menu, abra Conhecimento → Arquivos pessoais e toque em Selecionar Arquivos e Importar, ou adicione arquivos na configuração inicial. Os arquivos ficam em coleções, cada uma com seu interruptor: só as coleções ligadas são pesquisadas. Toque em Perguntar sobre eles, ou escolha o estilo Arquivos pessoais, e pergunte: sobre um arquivo pelo nome, ou peça um resumo de todos. Se seus arquivos não tiverem a resposta, o BOAR diz que não encontrou nos seus Arquivos pessoais, em vez de inventar. Seus arquivos ficam no celular.

## Pacotes de conhecimento
Pacotes de conhecimento são as bibliotecas que o BOAR pesquisa, guardadas no celular. O pacote Wikipedia Vital Articles tem mais de 50.000 artigos (cerca de 164 MB). Gerencie em menu → Conhecimento → Conhecimento BOAR (ou Configurações → Conhecimento): veja o que está instalado, use Baixar ou Remover. Um pacote é baixado uma vez; depois funciona offline.

## Fontes embaixo da resposta
Embaixo de cada resposta que usou a biblioteca, Fontes consultadas lista as fontes, numeradas, com o quanto cada uma combinou com a pergunta: artigos da biblioteca ou, com Arquivos pessoais, "Seus documentos". Toque numa fonte para ver o trecho. "Ler o artigo inteiro ›" abre o artigo ou arquivo completo, offline, com a parte que o BOAR usou marcada. Respostas rápidas, como cumprimentos e as respostas instantâneas dos labs, não têm fontes.

## Embaixo da resposta
Cada resposta pronta mostra quanto tempo levou e a velocidade com que foi escrita. Embaixo dela: ▲ e ▼ para avaliar, ⧉ para copiar, 💭 para ver o raciocínio do modelo quando houver, e 🧩 para as ações dos labs que você ligou.

## Modelos
O modelo de respostas é quem escreve as respostas. A configuração inicial recomenda um para a memória do seu celular: Qwen3 4B (cerca de 2,5 GB) em celulares com 12 GB de memória, Qwen3 1.7B (cerca de 1,1 GB) nos menores. Nos celulares de 12 GB, o LFM2.5 8B aparece marcado como a opção rápida. Abra Catálogo de Modelos no menu, ou Configurações → Modelos: veja o que está instalado, use Selecionar e Usar para trocar de modelo, Baixar para baixar um, ou Remover para apagar o que não precisa. Um selo colorido mostra se cada modelo cabe na memória do celular. Modelos maiores respondem melhor, mas são mais lentos e precisam de mais memória. O nome do modelo que está respondendo aparece embaixo de "BOAR", no topo da conversa.

## Encontrando mais modelos
Descobrir modelos GGUF, no Catálogo de Modelos e na configuração inicial, busca mais modelos no Hugging Face. Precisa de internet só enquanto busca e baixa, e as palavras da busca vão para o Hugging Face. Esses modelos não são verificados pelo BOAR. Modelos que exigem conta no Hugging Face não podem ser baixados pelo BOAR. Se um modelo talvez não caiba na memória do celular, o BOAR avisa antes de carregar e oferece carregar mesmo assim ou escolher outro modelo.

## Bloqueio por PIN
Um PIN (de 4 a 8 dígitos) tranca o BOAR e protege suas conversas. Defina em Configurações → Bloqueio por PIN (Definir um PIN, Trocar o PIN, Remover o PIN) ou na configuração inicial. Com o PIN ligado, o BOAR pede o PIN quando você abre o app e de novo depois de um minuto em segundo plano, suas conversas ficam guardadas criptografadas com uma chave que só o seu PIN abre, e a tela do BOAR fica em branco nos apps recentes, em capturas e em gravações de tela. Depois de várias tentativas erradas, o BOAR faz você esperar antes de tentar de novo, cada vez mais.

## Esqueci o PIN
Se você esquecer o PIN, toque em "Esqueceu o PIN?" na tela do PIN. Sem o PIN, suas conversas não podem ser abertas, nem por nós; por isso o BOAR oferece apagá-las, junto com seus Arquivos pessoais e o que os labs criaram, e depois remove o PIN. Toque em Apagar conversas e arquivos antes do fim da contagem para confirmar; senão, nada é apagado. Modelos e pacotes de conhecimento ficam. Se não der para apagar tudo, o PIN continua ativo para você tentar de novo.

## Notificações
Enquanto escreve uma resposta, o BOAR mostra a notificação "O BOAR está respondendo…"; toque nela para voltar. O Android pede sua permissão uma vez, na configuração inicial ou na conversa. Sem ela, as respostas continuam quando você troca de app; você só não vê que o BOAR está trabalhando. Para ligar depois, use Permitir notificações ou as configurações do Android para o BOAR.

## Aparência e tamanho do texto
Configurações → Exibição e tema muda o tema, o tamanho do texto (Compacto, Padrão, Grande) e a rotação da tela (Automática, Retrato, Paisagem; Retrato é o padrão). Moonlight (o padrão) e Campfire estão sempre disponíveis; outros temas aparecem conforme você usa o BOAR com o Boarverse ligado. O Feedback tátil, desligado por padrão, também fica aqui.

## Boarverse e Avatares do Boar
O Boarverse é uma camada opcional e divertida, desligada por padrão: Configurações → Experimental → Boarverse. Com ele ligado, usar o BOAR libera Avatares do Boar, personagens que você pode usar como a imagem do BOAR na conversa e no menu. Alguns avatares também trazem temas novos. Quando você libera um, o BOAR avisa "Novo avatar". Veja os seus e escolha um em Configurações → Avatares do Boar. Tudo é contado no celular e nunca sai dele. Desligar o Boarverse traz de volta a imagem original do BOAR; seus avatares continuam guardados.

## Boar Labs
O Boar Labs é um conjunto de experimentos: abra Boar Labs no menu, ou Configurações → Experimental → Boar Labs. Todo lab começa desligado; abra um para ler o que ele faz e ligue em Ativado. Cada lab mostra seu status: ATIVO, BETA (testado em celulares), ALPHA (inicial) ou EM REVISÃO (visível, mas parado). Alguns cartões são CONFIDENCIAIS: em teste, sem data de lançamento. Alguns labs respondem na hora, sem o modelo, e aí a resposta oferece "Perguntar ao BOAR". Labs com ações as colocam no 🧩 embaixo das respostas. Se um lab se comportar mal, desligue: ele some também da conversa.

## Os labs
- Boar Vitals: veja o celular trabalhar: calor, o clock de cada núcleo, a memória e o chip.
- Boar Calculadora: respostas exatas para contas do dia a dia (bateria, consumo, trilha, câmbio, água, temperatura, duração), na hora. Contas simples e porcentagens ainda vão para o modelo.
- Boar Fatos: um dado sobre um país, cidade, montanha, rio, monumento ou prêmio, de uma tabela da Wikidata.
- Boar Cohazard: uma resposta fixa de segurança sobre monóxido de carbono ao usar aparelhos a combustível em lugar fechado.
- Boar Distância: a distância entre duas cidades, e quanto tempo a pé, de bicicleta ou de carro, como estimativa.
- Boar Polyglot: avisa na hora quando você pergunta numa língua que o BOAR ainda não fala.
- Boar Designer: desenha uma ilustração a partir de poucas palavras, offline. Baixa o modelo de desenho (683 MB) na primeira vez.
- Boar Press: junte respostas numa cesta e compile tudo num PDF ou arquivo Markdown.
- Boar Respostas em Etapas: a resposta chega em etapas: o que as fontes dizem, uma resposta curta, os detalhes e a conferência dos números. Pare quando tiver o suficiente.
- Boar Canvas: transforma uma resposta num cartão (tabela, gráfico, passos ou fatos principais) ou num pequeno widget interativo, pelo 🧩 embaixo da resposta: Mostrar como cartão ou Montar um widget.
- Big Boar: roda um modelo muito grande (35B) a partir do armazenamento do celular. Lento, e um download de 12,3 GB.
- Boar Nomad: uma bússola com os sensores do celular, offline, sem precisar de localização.
- Boar Lunar: a lua hoje e neste mês: fase, quanto está iluminada, a próxima lua cheia e nova.
- Boar Guarda: corrige um sim/não ou vencedor que os próprios números ou datas do modelo contradizem.
- Boar Wisp: um motor minúsculo para um modelo pequeno; em revisão e parado nesta versão.

## Configurações
As Configurações têm estas seções, fechadas: Como o BOAR responde, Modelos, Conhecimento, Conversas e histórico, Exibição e tema, Avatares do Boar (com o Boarverse ligado), Idioma, Bloqueio por PIN, Entrada por voz, Este celular (com Mostrar detalhes técnicos ligado), Ajustes avançados, Experimental e Recomeçar. Ajustes avançados tem predefinições de velocidade, bateria e temperatura, e Mostrar detalhes técnicos, que adiciona a velocidade ao vivo no topo da conversa, barras de memória e armazenamento no menu e a seção Este celular.

## Conversas e histórico
Suas conversas ficam no celular e aparecem em Sessões recentes, no menu. Configurações → Conversas e histórico define quantas conversas o BOAR guarda, se ele dá nome a elas sozinho, se resume conversas longas em segundo plano, se pergunta antes de apagar uma conversa, e limpa o histórico. Apague uma conversa pelo menu. Enquanto o BOAR responde, pare a resposta para trocar de conversa. Com o PIN ligado, as conversas ficam criptografadas.

## Entrada por voz
Toque no microfone dentro da caixa de texto para falar a pergunta. O BOAR usa o reconhecimento de voz do próprio celular, sem internet; num celular sem ele, o microfone não aparece. Ligue ou desligue em Configurações → Entrada por voz.

## Idioma
As telas do BOAR estão em português e em inglês: escolha Sistema, English ou Português em Configurações → Idioma. Você pode perguntar em qualquer um dos dois; para uma pergunta em português, o BOAR também pesquisa a biblioteca em inglês.

## Privacidade
O BOAR roda no celular: perguntas, respostas, arquivos e conversas ficam nele. Ele só usa a internet para baixar os modelos e pacotes que você escolher, e para mandar as palavras da busca ao Hugging Face quando você usa Descobrir modelos GGUF. Ele nunca envia suas perguntas para lugar nenhum.

## Fechar o BOAR
Fechar o BOAR (libera memória), no menu, libera a memória que o modelo usa e fecha o app; se uma resposta ainda estiver sendo escrita, ele pergunta antes. Por padrão, o BOAR também libera o modelo assim que você sai do app e nenhuma resposta está rodando, para sobrar espaço para os outros apps: Configurações → Modelos → Liberar memória quando eu sair do BOAR.

## Recomeçar
Configurações → Recomeçar tem: Rodar a configuração de novo, Ver o tour, Redefinir configurações e Apagar tudo. Redefinir configurações volta tema, estilo de resposta, Pesquisa Profunda, Ajustes avançados e opções de exibição ao padrão e abre a configuração inicial; modelos, pacotes, conversas, PIN e idioma ficam. Apagar tudo apaga modelos, pacotes, seus arquivos, todas as conversas, as configurações e o PIN, fecha o BOAR, e a próxima abertura começa a configuração; não dá para desfazer.

## Sobre o BOAR
Toque na marca do BOAR no topo do menu para abrir Sobre o BOAR: a versão, os links do projeto e os créditos.

## Quando algo dá errado
- Uma resposta está lenta: tente Sucinto e Direto, um Máximo de tokens de saída menor, um modelo menor no Catálogo de Modelos, ou desligue a Pesquisa Profunda.
- Um modelo não carregou: toque em Tentar de novo com menos memória, ou escolha outro modelo; um modelo grande demais para a memória do celular pode falhar.
- Uma resposta foi cortada porque o BOAR fechou: ela diz que foi interrompida; toque em Tentar de novo.
- O download parou na configuração inicial: toque em Baixar de novo, ou em Tentar de novo quando o celular estiver online.
