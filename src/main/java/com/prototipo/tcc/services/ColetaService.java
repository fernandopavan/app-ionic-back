package com.prototipo.tcc.services;

import com.pi4j.component.temperature.TemperatureSensor;
import com.pi4j.gpio.extension.ads.ADS1115GpioProvider;
import com.pi4j.gpio.extension.ads.ADS1115Pin;
import com.pi4j.gpio.extension.ads.ADS1x15GpioProvider;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinAnalogInput;
import com.pi4j.io.gpio.event.GpioPinListenerAnalog;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.w1.W1Master;
import com.pi4j.temperature.TemperatureScale;
import com.prototipo.tcc.domain.Analise;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

@Service
public class ColetaService {

    @Autowired
    private AnaliseService analiseService;

    @Autowired
    private TratamentoService tratamentoService;

    @Autowired
    private UsuarioService usuarioService;

    private static GpioController gpio;

    @Async
    public void nova() throws InterruptedException, IOException, I2CFactory.UnsupportedBusNumberException {
        gpio = GpioFactory.getInstance();
        nova(null, Boolean.TRUE);
    }

    public void nova(Analise analiseResultado, Boolean tratar) throws InterruptedException, IOException, I2CFactory.UnsupportedBusNumberException {
//        UserSS user = UserService.authenticated();
//        if (user == null) {
//            throw new AuthorizationException("Acesso negado");
//        }

        LocalDateTime dataLeitura = LocalDateTime.now();

        BigDecimal ph = coletaPh();
        BigDecimal condutividade = coletaCondutividade();
        BigDecimal turbidez = coletaTurbidez();
        BigDecimal temperatura = coletaTemperatura();

        if (analiseResultado == null && tratar) {
            Analise analise = new Analise();
            analise.setUsuario(usuarioService.find(1));
            analise.setPh(ph);
            analise.setCondutividade(condutividade);
            analise.setTurbidez(turbidez);
            analise.setTemperatura(temperatura);
            analise.setDataLeitura(dataLeitura);

            analiseService.insert(analise);
            Analise analiseAposTratamento = tratamentoService.processa(analise);
            Analise updated = analiseService.update(analiseAposTratamento);

            nova(updated, Boolean.FALSE);
        }

        if (analiseResultado == null) {
            return;
        }

        analiseResultado.setPhNovo(ph);
        analiseResultado.setTurbidezNovo(turbidez);
        analiseResultado.setCondutividadeNovo(condutividade);
        analiseResultado.setDataLeituraNovo(dataLeitura);

        analiseService.update(analiseResultado, Boolean.TRUE);
    }

    private BigDecimal coletaTemperatura() {
        W1Master w1Master = new W1Master();

        for (TemperatureSensor device : w1Master.getDevices(TemperatureSensor.class)) {
            System.out.printf("- Leitura da temperatura: %3.1f°C %n", device.getTemperature(TemperatureScale.CELSIUS));
            return BigDecimal.valueOf(device.getTemperature(TemperatureScale.CELSIUS));
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal coletaTurbidez() throws IOException, I2CFactory.UnsupportedBusNumberException {
        System.out.println("- Iniciando leitura da turbidez");

        List<Double> coletaList = new ArrayList<>();

        final ADS1115GpioProvider gpioProvider = new ADS1115GpioProvider(I2CBus.BUS_1, ADS1115GpioProvider.ADS1115_ADDRESS_0x48);

        //Leitura do INPUT_A1
        GpioPinAnalogInput myInput = gpio.provisionAnalogInputPin(gpioProvider, ADS1115Pin.INPUT_A1, "MyAnalogInput-A1");

        gpioProvider.setProgrammableGainAmplifier(ADS1x15GpioProvider.ProgrammableGainAmplifierValue.PGA_4_096V, ADS1115Pin.INPUT_A1);
        gpioProvider.setEventThreshold(100, ADS1115Pin.INPUT_A1);
        gpioProvider.setMonitorInterval(100);

        GpioPinListenerAnalog listener = event -> {
            double value = event.getValue();
            double percent = ((value * 100) / ADS1115GpioProvider.ADS1115_RANGE_MAX_VALUE);
            Double voltage = gpioProvider.getProgrammableGainAmplifier(event.getPin()).getVoltage() * (percent / 100);
            coletaList.add(voltage);
        };

        myInput.addListener(listener);

        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        gpio.removeListener(listener, myInput);
        gpio.unprovisionPin(myInput);

        System.out.println("- Finalizando a leitura da turbidez");

        BigDecimal volts = getMediaLeituraAnalogica(coletaList);
        //volts = volts.multiply(BigDecimal.valueOf(0.8));
        BigDecimal turbidity = BigDecimal.valueOf(100).subtract(volts.multiply(BigDecimal.valueOf(100)));

        if (turbidity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return turbidity.setScale(2, BigDecimal.ROUND_HALF_DOWN);
    }

    private BigDecimal coletaCondutividade() throws IOException, I2CFactory.UnsupportedBusNumberException {
        System.out.println("- Iniciando leitura da condutividade");

        List<Double> coletaList = new ArrayList<>();

        final ADS1115GpioProvider gpioProvider = new ADS1115GpioProvider(I2CBus.BUS_1, ADS1115GpioProvider.ADS1115_ADDRESS_0x48);

        //Leitura do INPUT_A2
        GpioPinAnalogInput myInput = gpio.provisionAnalogInputPin(gpioProvider, ADS1115Pin.INPUT_A2, "MyAnalogInput-A2");

        gpioProvider.setProgrammableGainAmplifier(ADS1x15GpioProvider.ProgrammableGainAmplifierValue.PGA_4_096V, ADS1115Pin.INPUT_A2);
        gpioProvider.setEventThreshold(100, ADS1115Pin.INPUT_A2);
        gpioProvider.setMonitorInterval(100);

        GpioPinListenerAnalog listener = event -> {
            double value = event.getValue();
            double percent = ((value * 100) / ADS1115GpioProvider.ADS1115_RANGE_MAX_VALUE);
            Double voltage = gpioProvider.getProgrammableGainAmplifier(event.getPin()).getVoltage() * (percent / 100);
            coletaList.add(voltage);
        };

        myInput.addListener(listener);

        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        gpio.removeListener(listener, myInput);
        gpio.unprovisionPin(myInput);

        System.out.println("- Finalizando a leitura da condutividade");

        return getMediaLeituraAnalogica(coletaList);
    }

    private BigDecimal coletaPh() throws IOException, I2CFactory.UnsupportedBusNumberException {
        System.out.println("- Iniciando leitura do pH");

        List<Double> coletaList = new ArrayList<>();

        final ADS1115GpioProvider gpioProvider = new ADS1115GpioProvider(I2CBus.BUS_1, ADS1115GpioProvider.ADS1115_ADDRESS_0x48);

        //Leitura do INPUT_A0
        GpioPinAnalogInput myInput = gpio.provisionAnalogInputPin(gpioProvider, ADS1115Pin.INPUT_A0, "MyAnalogInput-A0");

        gpioProvider.setProgrammableGainAmplifier(ADS1x15GpioProvider.ProgrammableGainAmplifierValue.PGA_4_096V, ADS1115Pin.INPUT_A0);
        gpioProvider.setEventThreshold(100, ADS1115Pin.INPUT_A0);
        gpioProvider.setMonitorInterval(100);

        GpioPinListenerAnalog listener = event -> {
            double value = event.getValue();
            double percent = ((value * 100) / ADS1115GpioProvider.ADS1115_RANGE_MAX_VALUE);
            Double voltage = gpioProvider.getProgrammableGainAmplifier(event.getPin()).getVoltage() * (percent / 100);
            coletaList.add(voltage);
        };

        myInput.addListener(listener);

        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        gpio.removeListener(listener, myInput);
        gpio.unprovisionPin(myInput);

        System.out.println("- Finalizando a leitura do pH");

        // 14 -> 5v // x -> ?v
        BigDecimal volts = getMediaLeituraAnalogica(coletaList);

        return volts.multiply(BigDecimal.valueOf(14)).divide(BigDecimal.valueOf(5));
    }

    private BigDecimal getMediaLeituraAnalogica(List<Double> coletaList) {
        OptionalDouble media = coletaList.stream().mapToDouble(a -> a).average();
        return media.isPresent() ? BigDecimal.valueOf(media.getAsDouble()) : BigDecimal.ZERO;
    }
}
