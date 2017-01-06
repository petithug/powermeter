package org.implementers.powermeter;

import com.phidgets.*;
import com.phidgets.event.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * My main class.
 *
 * <p>I probably need to say more about that...
 */
public class Main {
	static class Measure {
		final double voltage;
		final double amperage;

		Measure(int voltage, int amperage) {
			this.voltage = ((double)voltage / 200 - 2.5) / 0.0681;
			this.amperage = (double)amperage / 13.2 - 37.8787;
			}

		public String toString() {
			return voltage + "V " + amperage + "A " + (voltage * amperage) + "W";
			}
		}

	private static Function<SensorChangeEvent, Stream<Measure>> convert() {
		int[] voltage = new int[1];
		int[] amperage = new int[1];

		return event -> {
			switch (event.getIndex()) {
				case 0:
					if (event.getValue() / 3 == amperage[0] / 3) {
						return null;
						}
					amperage[0] = event.getValue();
					break;

				case 1:
					if (event.getValue() / 3 == voltage[0] / 3) {
						return null;
						}
					voltage[0] = event.getValue();
					break;
				}
			if (voltage[0] != 0 && amperage[0] != 0) {
				return Stream.of(new Measure(voltage[0], amperage[0]));
				}
			return null;
			};
		}


	public static void main(String... args)
		throws Exception {

		InterfaceKitPhidget phidget = new InterfaceKitPhidget();
		BlockingQueue<SensorChangeEvent> queue = new LinkedBlockingQueue<>();
		phidget.addAttachListener(event -> {
			new Thread() {
				public void run() {
					try {
						while (true) {
							phidget.setRatiometric(true);
							Thread.sleep(50);
							queue.offer(new SensorChangeEvent(phidget, 0, phidget.getSensorValue(0)));
							phidget.setRatiometric(false);
							Thread.sleep(50);
							queue.offer(new SensorChangeEvent(phidget, 1, phidget.getSensorValue(1)));
							}
						}
					catch (PhidgetException | InterruptedException exception) {
						exception.printStackTrace();
						}
					}
				}.start();
			});
		phidget.addDetachListener(System.out::println);
		phidget.openAny();
		System.out.println("Time,Volt,Amp,Watt");
		Stream.generate(() -> {
			try {
				return queue.take();
				}
			catch (InterruptedException exception) {
				throw new RuntimeException(exception);
				}
			})
			.filter(event -> event.getIndex() == 0 || event.getIndex() == 1)
			.flatMap(convert())
			.forEach(measure -> System.out.printf("%1$tT.%1$tL,%2$f,%3$f,%4$f%n", System.currentTimeMillis(), measure.voltage, measure.amperage, measure.voltage * measure.amperage));
		}
	}

