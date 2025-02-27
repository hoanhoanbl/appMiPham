package javaNangCao;

public class ProducerConsumerProblem {
Store store;
Producer pro;
Consumer con;
public ProducerConsumerProblem() {
	pro=new Producer(store);
	con =new Consumer(store);
	store=new Store();
	pro.start();
	con.start();
}
public static void main(String[] args) {
	ProducerConsumerProblem obj=new ProducerConsumerProblem();
}
}
