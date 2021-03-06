package com.xupt.yzh.rpc.framework.provider;


import com.xupt.yzh.rpc.framework.protocol.RpcRequest;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Map;

public class ProcessorHandler implements Runnable {

    private Socket socket;
    private Map<String, Object> handlerMap;

    public ProcessorHandler(Socket socket,Map<String, Object> handlerMap) {
        this.socket = socket;
        this.handlerMap = handlerMap;
    }

    @Override
    public void run() {
        try {
            // Object~Stream也是包装类，其作用在于将字节流解析成java对象
            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            // 解析出具体的请求信息（RPCRequest）
            RpcRequest rpcRequest = (RpcRequest)objectInputStream.readObject();

            // 反射调用本地服务
            Object res = invoke(rpcRequest);

            // 将执行结果返回
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            objectOutputStream.writeObject(res);
            objectOutputStream.flush();  // 切记要flush手动刷新

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    // 通过反射具体执行provider提供的方法（即消费者要调用的方法）
    public Object invoke(RpcRequest request) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException {
        
        // todo（5）
        /**
         * 多了一个条件，从请求中拿到版本号，在寻找相应Bean时加上版本号
         */
        String serviceName = request.getClassName();
        String version = request.getVersion();
        if (!StringUtils.isEmpty(version)) {
            serviceName += "-" + version;
        }

        Object service = handlerMap.get(serviceName);
        
        // 若无相应服务则报错
        if (service == null) {
            throw new RuntimeException("server not found:" + service);
        }

        // 拿到所有参数，并获取他们的类型
        Object[] args = request.getParameters();
        Class<?>[] types = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }

        // 通过全类名拿到具体具体Class对象(HelloServiceImpl)
        Class<?> clazz = Class.forName(request.getClassName());

        // 通过methodName和参数类型拿到具体的Method（sayHello， saveUser）
        Method method = clazz.getMethod(request.getMethodName(), types);
        // 执行method
        // 注：这里其实也可以new一个对象后再执行具体方法
        Object res = method.invoke(service, args);

        return res;
    }
}
