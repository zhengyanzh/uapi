/**
 * Copyright (C) 2010 The UAPI Authors
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at the LICENSE file.
 *
 * You must gained the permission from the authors if you want to
 * use the project into a commercial product
 */

package uapi.sample;

import uapi.config.annotation.Config;
import uapi.config.internal.IntValueParser;
import uapi.log.ILogger;
import uapi.service.IRegistry;
import uapi.service.annotation.Init;
import uapi.service.annotation.Inject;
import uapi.service.annotation.Service;

/**
 * Created by xquan on 4/5/2016.
 */
@Service
public class ConfigurableService {

    @Config(path="name")
    String _name;

    @Config(path="age")
    int _age;

    @Config(path="address", parser=AddressParser.class)
    Address _address;

    @Inject
    ILogger _logger;

    @Inject
    IRegistry _svcReg;

    @Init
    public void init() {
        this._logger.info("Configured {} = {}", "name", this._name);
        this._logger.info("Configured {} = {}", "age", this._age);
        this._logger.info("Configured {} = {}", "address", this._address);
    }
}
