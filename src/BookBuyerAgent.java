import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class BookBuyerAgent extends Agent{
    //Название книги для покупки
    private String targetBookTitle;
    //Список известных продавцов агентов
    private AID[] sellerAgents = {new AID("seller1", AID.ISLOCALNAME),
            new AID("seller2", AID.ISLOCALNAME)};
    //инициализация агента
    protected void setup() {
        // Распечатка приветственного сообщения
        System.out.println("Hello! Buyer-agent " + getAID().getName() + " is ready.");
        // Получить название книги, чтобы купить, в качестве аргумента запуска
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            targetBookTitle = (String) args[0];
            System.out.println("Trying to buy " + targetBookTitle);
            // Добавить TickerBehaviour, который планирует запрос к агентам продавца каждую минуту
            addBehaviour(new TickerBehaviour(this, 60000) {
                protected void onTick() {
                    // Обновляем список агентов продавца
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("book-selling");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        sellerAgents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            sellerAgents[i] = result[i].getName();
                        }
                    }
                    catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    // Выполнить запрос
                    myAgent.addBehaviour(new RequestPerformer());
                }
            } );
        } else {
            // Иначе немедленно останавливаем агента
            System.out.println("No book title specified");
            doDelete();
        }
    }

    // Операции очистки агента
    protected void takeDown() {
        // Распечатка сообщения об увольнении агента
        System.out.println("Buyer-agent " + getAID().getName() + " terminating.");
    }

    /**
     Внутренний класс RequestPerformer. Это поведение, используемое агентами покупателя книги для запроса агенту
     продавца целевой книги.
     */
    private class RequestPerformer extends Behaviour {
        private AID bestSeller; // Агент, который предоставляет лучшее предложение
        private int bestPrice; // Лучшая цена
        private int repliesCnt = 0; // Счетчик ответов от агентов-продавцов
        private MessageTemplate mt; // Шаблон для получения ответов
        private int step = 0;
        public void action() {
            switch (step) {
                case 0:
                    // Отправляем cfp всем продавцам
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < sellerAgents.length; ++i) {
                        cfp.addReceiver(sellerAgents[i]);
                    }
                    cfp.setContent(targetBookTitle);
                    cfp.setConversationId("book-trade");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique value
                    myAgent.send(cfp);
                    // Готовим шаблон для получения предложений
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    // Получать все предложения / отказы от агентов продавца
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Ответ получен
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            // Это предложение
                            int price = Integer.parseInt(reply.getContent());
                            if (bestSeller == null || price < bestPrice) {
                                // Это лучшее предложение на данный момент
                                bestPrice = price;
                                bestSeller = reply.getSender();
                            }
                        }
                        repliesCnt++;
                        if (repliesCnt >= sellerAgents.length) {
                            // Мы получили все ответы
                            step = 2;
                        }
                    }
                    else {
                        block();
                    }
                    break;
                case 2:
                    // Отправить заказ на покупку продавцу, предоставившему лучшее предложение
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestSeller);
                    order.setContent(targetBookTitle);
                    order.setConversationId("book-trade");
                    order.setReplyWith("order" + System.currentTimeMillis());
                    myAgent.send(order);
                    // Готовим шаблон для получения ответа на заказ на покупку
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 3;
                    break;
                case 3:
                    // Получить ответ на заказ на покупку
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Получен ответ на заказ на покупку
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            // Покупка прошла успешно. Мы можем прекратить
                            System.out.println(targetBookTitle + " successfully purchased.");
                            System.out.println("Price = " + bestPrice);
                            myAgent.doDelete();
                        }
                        step = 4;
                    }
                    else {
                        block();
                    }
                    break;
            }
        }
        public boolean done() {
            return ((step == 2 && bestSeller == null) || step == 4);
        }
    } // Конец внутреннего класса RequestPerformer
}


