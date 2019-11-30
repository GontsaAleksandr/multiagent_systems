import jade.core.Agent;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;
public class BookSellerAgent extends Agent {
    // Каталог книг для продажи (сопоставляет название книги с ее ценой)
    private Hashtable catalogue;
    // GUI, с помощью которого пользователь может добавлять книги в каталог
    private BookSellerGui myGui;
    // Ложим инициализации агента здесь
    protected void setup() {
        // Создаем каталог
        catalogue = new Hashtable();
        // Создать и показать графический интерфейс
        myGui = new BookSellerGui(this);
        myGui.show();
        // Добавляем поведение, обслуживающее запросы на предложение от агентов покупателя
        addBehaviour(new OfferRequestsServer());
        // Добавляем поведение, обслуживающее заказы на покупку от агентов покупателя
        addBehaviour(new PurchaseOrdersServer());
        // Зарегистрируем сервис продажи книг на желтых страницах
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("book-selling");
        sd.setName("JADE-book-trading");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

    }
    //Операции очистки агента
    protected void takeDown() {
        // Закрыть GUI
        myGui.dispose();
        // Распечатка сообщения об увольнении
        System.out.println("Seller-agent " + getAID().getName() + " terminating.");
    }
    //Вызывается GUI, когда пользователь добавляет новую книгу для продажи
    public void updateCatalogue(final String title, final int price) {
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                catalogue.put(title, new Integer(price));
            }
        } );
    }

    /**
     Внутренний класс OfferRequestsServer. Это поведение, используемое агентами продавца книг
     для обслуживания входящих запросов предложений от агентов покупателей. Если запрошенная
     книга находится в локальном каталоге, продавец-продавец отвечает сообщением ПРЕДЛОЖЕНИЕ
     с указанием цены. В противном случае сообщение REFUSE отправляется обратно.
     */

    private class OfferRequestsServer extends CyclicBehaviour {
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                // Сообщение получено. Обработать его
                String title = msg.getContent();
                ACLMessage reply = msg.createReply();
                Integer price = (Integer) catalogue.get(title);
                if (price != null) {
                    // Запрошенная книга доступна для продажи. Ответить с ценой
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(String.valueOf(price.intValue()));
                }
                else {
                    // Запрошенная книга НЕ доступна для продажи.
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            }else {
                block();
            }

        }
    } // Конец внутреннего класса OfferRequestsServer

    /**
     Inner class PurchaseOrdersServer.
     This is the behaviour used by Book-seller agents to serve incoming
     offer acceptances (i.e. purchase orders) from buyer agents.
     The seller agent removes the purchased book from its catalogue
     and replies with an INFORM message to notify the buyer that the
     purchase has been sucesfully completed.
     */
    private class PurchaseOrdersServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // ACCEPT_PROPOSAL Сообщение получено. Обработать его
                String title = msg.getContent();
                ACLMessage reply = msg.createReply();

                Integer price = (Integer) catalogue.remove(title);
                if (price != null) {
                    reply.setPerformative(ACLMessage.INFORM);
                    System.out.println(title+" sold to agent "+msg.getSender().getName());
                }
                else {
                    // Запрошенная книга была продана другому покупателю.
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }  // Конец внутреннего класса OfferRequestsServer
}

